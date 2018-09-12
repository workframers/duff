(ns rf-form.core-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [rf-form.core :as rf]
            [rf-utils.core :as rfu]))

(deftest testing
  (testing "ok"
    (is (= "yea" "yea"))))

(comment
  (enable-console-print!)
  (run-tests))
