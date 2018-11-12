(ns examples.simple
  (:require [rf-forms.core :as rfu]))

(defn basic-input [{:keys [value on-change errors pristine?]}]
  [:input {:on-change on-change :value value}])

(defn simple-form* [{:keys [handle-submit]}]
  [:form {:on-submit handle-submit}
   [:div
    [rfu/field {:name      :email
                :component basic-input}]]
   [:button {:type "submit"} "Submit"]])

(def simple-form
  (rfu/create-form {:form-name :simple-form} simple-form*))
