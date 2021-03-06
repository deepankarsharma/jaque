(ns jaque.interpreter-test
  (:refer-clojure :exclude [atom])
  (:require [clojure.test :refer :all]
            [jaque.noun :refer :all])
  (:import net.frodwith.jaque.Bail
           net.frodwith.jaque.truffle.Context
           (net.frodwith.jaque.truffle.driver Arm AxisArm)
           (net.frodwith.jaque.truffle.nodes.jet DecrementNodeGen AddNodeGen SubtractNodeGen LessThanNodeGen)))

(def math-kernel-formula
  (noun [7 [1 :kmat] 7 [8 [1 1 :kmat] 10 [:fast 1 :kmat [1 0] 0] 0 1] 8 [1 [7 [8 [1 0 0] [1 6 [5 [1 0] 0 12] [0 13] 9 2 [0 2] [[8 [9 47 0 7] 9 2 [0 4] [0 28] 0 11] 4 0 13] 0 7] 0 1] 10 [:fast 1 :add [0 7] 0] 0 1] [7 [8 [1 0 0] [1 6 [6 [5 [0 12] 0 13] [1 1] 1 0] [6 [8 [1 6 [5 [1 0] 0 28] [1 0] 6 [6 [6 [5 [1 0] 0 29] [1 1] 1 0] [6 [9 2 [0 2] [0 6] [[8 [9 47 0 15] 9 2 [0 4] [0 60] 0 11] 8 [9 47 0 15] 9 2 [0 4] [0 61] 0 11] 0 15] [1 0] 1 1] 1 1] [1 0] 1 1] 9 2 0 1] [1 0] 1 1] 1 1] 0 1] 10 [:fast 1 :lth [0 7] 0] 0 1] [7 [8 [1 0] [1 10 [:memo 1 0] 8 [1 6 [8 [9 10 0 15] 9 2 [0 4] [[0 30] 7 [0 3] 1 3] 0 11] [1 1] 8 [8 [9 47 0 15] 9 2 [0 4] [0 30] 0 11] 8 [9 4 0 31] 9 2 [0 4] [[7 [0 3] 9 2 [0 6] [0 14] [0 2] 0 31] 7 [0 3] 9 2 [0 6] [0 14] [8 [9 47 0 31] 9 2 [0 4] [0 6] 0 11] 0 31] 0 11] 9 2 0 1] 0 1] 10 [:fast 1 :fib [0 7] 0] 0 1] [7 [8 [1 0 0] [1 6 [5 [1 0] 0 13] [0 12] 9 2 [0 2] [[8 [9 47 0 7] 9 2 [0 4] [0 28] 0 11] 8 [9 47 0 7] 9 2 [0 4] [0 29] 0 11] 0 7] 0 1] 10 [:fast 1 :sub [0 7] 0] 0 1] 7 [8 [1 0] [1 6 [5 [1 0] 0 6] [0 0] 8 [1 0] 8 [1 6 [5 [0 30] 4 0 6] [0 6] 9 2 [0 2] [4 0 6] 0 7] 9 2 0 1] 0 1] 10 [:fast 1 :dec [0 7] 0] 0 1] 10 [:fast 1 :math [0 3] [:add 9 4 0 1] [:sub 9 46 0 1] [:dec 9 47 0 1] [:fib 9 22 0 1] 0] 0 1]))

(def context (Context. (into-array Arm 
                                   [(AxisArm. "kmat/math/dec" 2 DecrementNodeGen)
                                    (AxisArm. "kmat/math/add" 2 AddNodeGen)
                                    (AxisArm. "kmat/math/sub" 2 SubtractNodeGen)
                                    (AxisArm. "kmat/math/lth" 2 LessThanNodeGen)])))

(defn nock [bus fol]
  (.nock context bus fol))

(deftest test-nock
  (testing "examples from nock tutorial"
    (doseq [[[sub fom] res msg]
            [[[[[4 5] [6 14 15]] [0 7]]
              [14 15]
              "sky blue, sun east"]
             [[42 [1 153 218]]
              [153 218]
              "constant operator"]
             [[77 [2 [1 42] [1 1 153 218]]]
              [153 218]
              "stupid use of 2"]
             [[57 [0 1]]
              57
              "fragment"]
             [[[132 19] [0 3]]
              19
              "fragment 2"]
             [[57 [4 0 1]]
              58
              "increment"]
             [[[132 19] [4 0 3]]
              20
              "increment2"]
             [[42 [4 0 1]]
              43
              "increment again"]
             [[42 [[4 0 1] [3 0 1]]]
              [43 1]
              "autocons"]
             [[[132 19] [10 37 [4 0 3]]]
              20
              "hint operator"]
             [[42 [7 [4 0 1] [4 0 1]]]
              44
              "composed (7) increment"]
             [[[10 20] [8 [0 2] [5 [0 2] [0 6]]]]
              0
              "push equals"]
             [[42 [8 [4 0 1] [0 1]]]
              [43 42]
              "op 8"]
             [[42 [6 [1 0] [4 0 1] [1 233]]]
              43
              "conditional yes"]
             [[42 [6 [1 1] [4 0 1] [1 233]]]
              233
              "conditional no"]
             [[42 [8 [1 0] 8 [1 6 [5 [0 7] 4 0 6] [0 6] 9 2 [0 2] [4 0 6] 0 7] 9 2 0 1]]
              41
              "decrement"]
             ]]
      (is (= (nock (noun sub) (noun fom)) (noun res)) msg)))

  (testing "bad-fragment"
    (is (thrown? Bail (nock 0 (noun [0 0])))))

  (testing "math-kernel"
    (let [ken (nock 0 math-kernel-formula)
          r   (nock ken (noun [8 [9 22 0 1] 9 2 [0 4] [1 15] 0 11]))]
      (is (= 610 r)))))
