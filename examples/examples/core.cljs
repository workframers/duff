(ns examples.core
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [examples.theme]
            [wonka.core :as w]
            [examples.simple :refer [simple-form]]))

(defn examples-root []
  [:div {:class (w/css {:background-color :ex.colors/pink})}
   [simple-form {:on-submit #(js/alert %)}]])

(defn mount-root []
  (r/render [examples-root] (.getElementById js/document "app")))

(defn ^:export main []
  (mount-root))
