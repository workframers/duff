(ns duff.core
  (:require
   ["prop-types" :as prop-types]
   [clojure.string :refer [join]]
   [goog.object :as obj]
   [rf-utils.core :as rfu :refer [collify]]
   [re-frame.core :as rf]
   [reagent.core :as r]))

(def noop (constantly nil))

(def component-partial
  "`component-partial` takes a component and map of props, and returns a component}
   that when rendered, will merge the partially applied map of props to the props
   passed in at runtime. This is useful if you need to partially apply props to a
   component before passing it a helper which will render it later with additional
   props (e.g. form utils).

   We use memoize so that you can partially apply components
   in render methods. If we didn't memoize, a new version would be created every
   re-render. This is particularly bad if the component you're using it on is a
   form input. because every value change will cause the input to lose focus.

   (def header (component-partial some-other-header {:size :small}))
   [header {:style {:margin-top 5}}] => props passed to `some-other-header`

   would be {:style {:margin-top 5} :size :small}
  "
  (memoize
    (fn [c p & children]
      (fn []
        (let [this     (r/current-component)
              props    (r/props this)]
          (into [c (r/merge-props props p)]
                children))))))

(defn clean [data]
  "removes util specific properties from a map. used to clean props before
  being passed along to native react components"
  (dissoc data
    :errors
    :pristine?
    :value?
    :dirty?
    :initial-value
    :submitted?
    :field-name
    :errors?))

(defn query
  [form-name query-key]
  (let [form-state (clean (rfu/get-in [:forms form-name]))
        get*       (fn [& path]
                     (rfu/get-in (into [:forms form-name] path)))]
    (case query-key
      :pristine?       (= form-state (get* :initial-value))
      :errors          (get* :errors)
      :dirty?          (not (query form-name :pristine?))
      :value           form-state
      :valid?          (empty? (get* :errors))
      :invalid         (not (empty? (get* :errors)))
      :submitted?      (get* :submitted?)
      :should-confirm? (and (query form-name :dirty?)
                            (not (query form-name :submitted?)))
      nil)))

(defn initialize-state
  "fired once when create-form mounts"
  [{:keys [name value validate initial-value]}]
  (if (rfu/get :active-forms)
    (rfu/update-sync :active-forms conj name)
    (rfu/assoc-sync :active-forms #{name}))
  (rfu/assoc-in-sync [:forms name] (merge value {:errors        (validate value)
                                                 :initial-value (or initial-value {})})))

(rf/reg-sub
  :forms/active-form?
  (fn [db [_ form-name]]
    (when-let [forms (:active-forms db)]
      (forms form-name))))

;; generic on-change handler for form events, semi-private
;; used internally by the field component
(rf/reg-event-fx
  ::on-change
  (fn [{:keys [db]} [_ {:keys [value form-name field-name validate-fn]
                        :as   event-args}]]
    (let [new-db (update-in db [:forms form-name] (fn [form-state]
                                                    (let [new-form-state (assoc form-state field-name value)]
                                                      (assoc new-form-state
                                                        :errors
                                                        (validate-fn new-form-state)))))]
      {:db new-db})))

(defn merge-props [form-props rest]
  "cleans form props from the first object"
  (r/merge-props (clean form-props) rest))

(defn make-synthetic-event [value]
  "
    Helper to make synthetic event when writing custom form components

    Native form components call event handlers with an object that looks like `e.target.value`
    It may be good to wrap values in that structure when building custom form components that
    do not eventually render a native form component e.g. our select dropdown component.
  "
  #js {"target" #js {"value" value}})

;; child context prop type object
(def context-shape #js {:form-name         prop-types/object.isRequired
                        :get-initial-value prop-types/func.isRequired
                        :validate          prop-types/func.isRequired})

(defn make-validation [spec]
  "
    Usage:

    (def validation (fu/make-validation {:title           [{:pred empty?
                                                            :text \"Enter title to save changes\"}
                                                           {:pred (fn [val] (> 5 (count val)))
                                                            :text \"must be more than five characters\"}]
                                        :address/line-1   :required ;; <- todo , keyword shortcuts
                                        :address/city     :required
                                        :address/zip-code :required}))
  "
  (fn [vals]
    (let [result (reduce-kv (fn [acc field-key fns*]
                              (let [fns       (collify fns*)
                                    field-val (get vals field-key)
                                    errors    (mapv (fn [validation]
                                                      (if (map? validation)
                                                        (let [{:keys [pred text]} validation]
                                                          (when (pred field-val)
                                                            text))
                                                        (validation field-val)))
                                                fns)]
                                (if (every? nil? errors)
                                  acc
                                  (assoc acc field-key errors))))
                   {}
                   spec)]
      (when (not (empty? result))
        result))))

;;TODO refactor and use this in field function
(defn- get-field-props [ctx field-name]
  (let [form-name           (obj/get ctx "form-name")
        get-initial-value   (obj/get ctx "get-initial-value")
        validate-fn         (obj/get ctx "validate")
        getter-fn           #(obj/getValueByKeys % "target" "value")
        path-in-state       [:forms form-name field-name]
        form-value          [:forms form-name]
        initial-field-value (get (get-initial-value) field-name)
        field-value         (rfu/get-in path-in-state)
        errors              (rfu/get-in [:forms form-name :errors field-name])
        pristine?           (= field-value initial-field-value)
        dirty?              (not pristine?)]
    {:initial-value initial-field-value
     :value         field-value
     :errors        errors
     :pristine?     pristine?
     :on-change     #(rf/dispatch [::on-change {:value       (getter-fn %)
                                                :form-name   form-name
                                                :validate-fn validate-fn
                                                :field-name  field-name}])
     :dirty?        dirty?}))

(defn connect-to-fields
  ([fields component]
   (connect-to-fields fields component nil))
  ([fields component opts]
   (fn [props]
     (r/create-class
      {:display-name (str "Connect-to-fields: " (join ", " (map name fields)))

       :context-types context-shape

       :reagent-render
       (fn [props]
         (let [this         (r/current-component)
               ctx          (obj/get this "context")
               fields-props (reduce (fn [acc field-name]
                                      (assoc acc field-name (get-field-props ctx field-name)))
                                    {}
                                    fields)]
           [component (merge props fields-props opts)]))}))))

(defn field [{:keys [name]}]
  "Wraps a dumb form component and connects it to the global form state"
  (r/create-class
    {:display-name  (str "Field: " name)

     :context-types context-shape

     :reagent-render
     (fn [{:keys [name component format parse getter sync?]
           :or   {format identity
                  parse  identity
                  getter #(obj/getValueByKeys % "target" "value")}
           :as   props}]
       (let [this                (r/current-component)
             ctx                 (obj/get this "context")
             form-name           (obj/get ctx "form-name")
             get-initial-value   (obj/get ctx "get-initial-value")
             validate-fn         (obj/get ctx "validate")
             path-in-state       [:forms form-name name]
             initial-field-value (get (get-initial-value) name)
             field-value         (rfu/get-in path-in-state)
             dispatch-fn         (if (false? sync?) rf/dispatch rf/dispatch-sync)
             on-change           #(dispatch-fn [::on-change {:value       (parse (getter %))
                                                             :form-name   form-name
                                                             :validate-fn validate-fn
                                                             :field-name  name}])
             errors              (rfu/get-in [:forms form-name :errors name])
             pristine?           (= field-value initial-field-value)
             dirty?              (not pristine?)
             value               (format field-value)
             children            (-> (r/current-component) r/children)]
         (into [component (merge {:value      value
                                  :value?     (if (seq? value)
                                                (not (empty? value))
                                                (not (nil? value)))
                                  :pristine?  pristine?
                                  :dirty?     dirty?
                                  :errors     errors
                                  :errors?    (and errors dirty?)
                                  :field-name name
                                  :on-change  on-change}
                            (dissoc props :name :component :format :parse :getter :sync?))]
           children)))}))

(defn wrap-field [path component]
  (let [opts (if (fn? component)
               {:component component}
               component)]
    [field (merge {:name path}
             opts)]))

(defn make-fields-map [spec]
  (reduce-kv (fn [acc k v]
               (assoc acc k (wrap-field k v)))
    {}
    spec))

(defn create-form [{:keys [form-name
                           initial-value
                           validate
                           persist?
                           fields]
                    :or   {initial-value noop
                           validate      noop}
                    :as   initial-config}
                   component]
  "The initial higher order form component. Serves as the form root, providing
   context data to children through the context api"
  (fn []
    (let [this              (r/current-component)
          props             (r/props this)
          get-initial-value #(if (some true? ((juxt fn? keyword?) initial-value))
                               (initial-value props)
                               initial-value)
          _                 (initialize-state {:name          form-name
                                               :value         (get-initial-value)
                                               :initial-value (get-initial-value)
                                               :validate      validate})]
      (r/create-class
        {:display-name (str "Form: " (name form-name))

         :get-child-context (fn []
                              (this-as this
                                #js {:form-name         form-name
                                     :get-initial-value get-initial-value
                                     :validate          validate}))

         :child-context-types context-shape

         :component-will-unmount
         (fn []
           (rfu/update :active-forms disj form-name)
           (when (not persist?)
             (rfu/dissoc-in [:forms form-name])))

         :reagent-render
         (fn []
           (let [this          (r/current-component)
                 {:keys [on-submit before-submit]
                  :or   {before-submit (fn [_ done] (done))}
                  :as   props} (r/props this)
                 state         (query form-name :value)
                 initial-value (get-initial-value)
                 pristine?     (= state initial-value)
                 errors        (query form-name :errors)
                 submitted?    (query form-name :submitted?)
                 valid?        (empty? errors)
                 invalid?      (not valid?)]
             [component (merge {:handle-submit (fn [e]
                                                 (let [state (query form-name :value)
                                                       valid? (empty? errors)]
                                                   (.preventDefault e)
                                                   (when valid?
                                                     (before-submit state
                                                                    (fn []
                                                                      (rfu/assoc-in-sync [:forms form-name :submitted?] true)
                                                                      (on-submit state))))))
                                :values        state
                                :pristine?     pristine?
                                :errors        errors
                                :dirty?        (not pristine?)
                                :reset         #(initialize-state {:name          form-name
                                                                   :value         initial-value
                                                                   :initial-value initial-value
                                                                   :validate      validate})
                                :submitted?    submitted?
                                :valid?        valid?
                                :invalid?      invalid?
                                :disabled?     (or submitted? invalid?)}
                          (when fields {:fields (make-fields-map fields)})
                          props)]))}))))
