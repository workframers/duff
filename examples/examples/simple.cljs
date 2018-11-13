(ns examples.simple
  (:require [duff.core :as d]
            [wonka.core :as w]))

(defn basic-input [{:keys [value on-change errors pristine?]}]
  [:input {:on-change on-change :value value}])

(defn simple-form* [{:keys [handle-submit]}]
  [:form {:on-submit handle-submit}
   [:div
    [d/field {:name      :email
                :component basic-input}]]
   [:button {:type "submit"} "Submit"]])

(def simple-form
  (d/create-form {:form-name :simple-form} simple-form*))
