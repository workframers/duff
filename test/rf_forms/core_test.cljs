(ns rf-forms.core-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [rf-forms.core :as rf]
            [rf-utils.core :as rfu]))

(deftest testing
  (testing "they are equal"
    (is (= "yea" "yea"))))

(comment
  (enable-console-print!)
  (run-tests))
