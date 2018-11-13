(ns examples.theme
  (:require [wonka.core :as w]))

(def theme
  {:ex.colors/pink  "pink"
   :ex.colors/black "323232"})

(w/insert-reset!)

(w/config {:theme        theme
           :theme-prefix "ex"})


