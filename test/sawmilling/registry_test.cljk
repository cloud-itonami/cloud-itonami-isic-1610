(ns sawmilling.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [sawmilling.registry :as r]))

;; ----------------------------- equipment-verified? / equipment-registered? / equipment-ready? -----------------------------

(deftest equipment-is-verified-when-flagged
  (is (true? (r/equipment-verified? {:id "e1" :verified? true}))))

(deftest equipment-is-not-verified-when-false-or-missing
  (is (false? (r/equipment-verified? {:id "e1" :verified? false})))
  (is (false? (r/equipment-verified? {:id "e1"}))))

(deftest equipment-is-registered-when-flagged
  (is (true? (r/equipment-registered? {:registered? true}))))

(deftest equipment-is-not-registered-when-false-or-missing
  (is (false? (r/equipment-registered? {:registered? false})))
  (is (false? (r/equipment-registered? {}))))

(deftest equipment-ready-requires-both
  (is (true? (r/equipment-ready? {:verified? true :registered? true})))
  (is (false? (r/equipment-ready? {:verified? true :registered? false})))
  (is (false? (r/equipment-ready? {:verified? false :registered? true})))
  (is (false? (r/equipment-ready? {}))))

;; ----------------------------- batch-verified? / batch-registered? / batch-ready? -----------------------------

(deftest batch-is-verified-when-flagged
  (is (true? (r/batch-verified? {:id "b1" :verified? true}))))

(deftest batch-is-not-verified-when-false-or-missing
  (is (false? (r/batch-verified? {:id "b1" :verified? false})))
  (is (false? (r/batch-verified? {:id "b1"}))))

(deftest batch-is-registered-when-flagged
  (is (true? (r/batch-registered? {:registered? true}))))

(deftest batch-is-not-registered-when-false-or-missing
  (is (false? (r/batch-registered? {:registered? false})))
  (is (false? (r/batch-registered? {}))))

(deftest batch-ready-requires-both
  (is (true? (r/batch-ready? {:verified? true :registered? true})))
  (is (false? (r/batch-ready? {:verified? true :registered? false})))
  (is (false? (r/batch-ready? {:verified? false :registered? true})))
  (is (false? (r/batch-ready? {}))))

;; ----------------------------- shipment-volume-exceeded? -----------------------------

(deftest small-shipment-within-volume-does-not-exceed
  (is (false? (r/shipment-volume-exceeded?
               {:volume-board-ft 50000.0 :shipped-volume-board-ft 10000.0} 5000.0))))

(deftest shipment-that-pushes-past-volume-exceeds
  (is (true? (r/shipment-volume-exceeded?
              {:volume-board-ft 8000.0 :shipped-volume-board-ft 7500.0} 1000.0))))

(deftest shipment-exactly-at-volume-does-not-exceed
  (is (false? (r/shipment-volume-exceeded?
               {:volume-board-ft 8000.0 :shipped-volume-board-ft 7500.0} 500.0))
      "exactly at volume is not over, only strictly beyond"))

(deftest missing-volume-is-not-flagged-exceeded
  (is (false? (r/shipment-volume-exceeded? {} 100.0)))
  (is (false? (r/shipment-volume-exceeded? {:volume-board-ft 800.0} nil))))

;; ----------------------------- grade-valid? -----------------------------

(deftest known-grades-are-valid
  (doseq [g [:select-structural :no1 :no2 :no3 :stud :construction :standard :economy]]
    (is (r/grade-valid? g))))

(deftest fabricated-grade-is-invalid
  (is (not (r/grade-valid? :premium-plus-select)))
  (is (not (r/grade-valid? nil))))

;; ----------------------------- moisture-content-valid? -----------------------------

(deftest typical-moisture-content-is-valid
  (is (r/moisture-content-valid? 15.0))
  (is (r/moisture-content-valid? 0.0))
  (is (r/moisture-content-valid? 200.0))
  (is (r/moisture-content-valid? 250.0)))

(deftest negative-moisture-content-is-invalid
  (is (not (r/moisture-content-valid? -1.0))))

(deftest excessive-moisture-content-is-invalid
  (is (not (r/moisture-content-valid? 999.0)))
  (is (not (r/moisture-content-valid? 250.01))))

(deftest non-numeric-or-missing-moisture-content-is-invalid
  (is (not (r/moisture-content-valid? nil)))
  (is (not (r/moisture-content-valid? "15.0"))))

;; ----------------------------- register-maintenance -----------------------------

(deftest maintenance-is-a-draft-not-a-real-actuation
  (let [result (r/register-maintenance "mnt-1" "equip-001" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest maintenance-assigns-maintenance-number
  (let [result (r/register-maintenance "mnt-1" "equip-001" 7)]
    (is (= (get result "maintenance_number") "MNT-000007"))
    (is (= (get-in result ["record" "maintenance_id"]) "mnt-1"))
    (is (= (get-in result ["record" "equipment_id"]) "equip-001"))
    (is (= (get-in result ["record" "kind"]) "maintenance-schedule-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest maintenance-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "" "equip-001" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "mnt-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "mnt-1" "equip-001" -1))))

;; ----------------------------- register-shipment -----------------------------

(deftest shipment-is-a-draft-not-a-real-dispatch
  (let [result (r/register-shipment "ship-1" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest shipment-assigns-shipment-number
  (let [result (r/register-shipment "ship-1" 7)]
    (is (= (get result "shipment_number") "SHP-000007"))
    (is (= (get-in result ["record" "shipment_id"]) "ship-1"))
    (is (= (get-in result ["record" "kind"]) "shipment-coordination-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest shipment-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "ship-1" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-maintenance "mnt-1" "equip-001" 0)
        hist (r/append [] c1)
        c2 (r/register-maintenance "mnt-2" "equip-001" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "MNT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "MNT-000001" (get-in hist2 [1 "record_id"])))))

;; ---------------------------------------------------------------------------
;; Capacity headroom: exact at the boundary, and un-checkable is not headroom
;; ---------------------------------------------------------------------------

(deftest a-shipment-filling-a-batch-exactly-is-not-over-capacity
  (testing "`(> (+ (double so-far) (double new)) (double capacity))` flagged 43
            of 2,865 exactly-at-capacity shipments as over, because the sum is
            not the double nearest the true total"
    (doseq [[cap so-far] [[209.67 60.8043] [100.0 33.33] [1000.10 0.07]
                          [55.96 10.0] [30.09 10.03]]]
      (let [new (- cap so-far)]
        (is (not (r/shipment-volume-exceeded? {:volume-board-ft cap
                                               :shipped-volume-board-ft so-far}
                                              new))
            (str so-far " + " new " should fill " cap " exactly, not exceed it"))))))

(deftest an-exhaustive-boundary-sweep-finds-no-false-over-capacity
  (let [bad (for [cap-c (range 10000 200000 997)
                  frac (range 1 100 7)
                  :let [cap (/ cap-c 100.0)
                        so-far (/ (* cap-c frac) 10000.0)
                        new (- cap so-far)]
                  :when (r/shipment-volume-exceeded?
                         {:volume-board-ft cap :shipped-volume-board-ft so-far} new)]
              [cap so-far new])]
    (is (empty? bad) (str "false over-capacity: " (count bad) " e.g. " (first bad)))))

(deftest a-genuine-overshoot-is-still-caught
  (is (r/shipment-volume-exceeded? {:volume-board-ft 100.0 :shipped-volume-board-ft 60.0} 40.01))
  (is (r/shipment-volume-exceeded? {:volume-board-ft 100.0 :shipped-volume-board-ft 0.0} 100.0001)))

(deftest un-checkable-headroom-is-reported-rather-than-passing-as-not-over
  (testing "the predicate's own guard made every un-checkable case fall through
            as `not over`, so a batch with no recorded capacity shipped anything"
    (is (not (r/shipment-volume-checkable? {:shipped-volume-board-ft 0.0} 10.0))
        "no recorded capacity")
    (is (not (r/shipment-volume-checkable? {:volume-board-ft 100.0} nil))
        "no stated shipment volume")
    (is (not (r/shipment-volume-checkable? {:volume-board-ft "100"} 10.0))
        "non-numeric capacity")
    (is (not (r/shipment-volume-checkable? nil 10.0))
        "no batch at all")
    (is (r/shipment-volume-checkable? {:volume-board-ft 100.0 :shipped-volume-board-ft 10.0} 5.0)
        "a fully recorded batch IS checkable")))
