(ns tech.compute.cpu.tensor-math.binary-accum
  (:require [tech.parallel :as parallel]
            [tech.compute.cpu.tensor-math.nio-access
             :refer [b-put b-get datatype-iterator
                     store-datatype-cast-fn
                     read-datatype-cast-fn
                     item->typed-nio-buffer
                     all-datatypes
                     datatype->cast-fn] :as nio-access]
            [tech.compute.cpu.tensor-math.addressing
             :refer [get-elem-dims->address
                     max-shape-from-dimensions]]
            [tech.datatype.java-unsigned :as unsigned]
            [tech.datatype.java-primitive :as primitive]
            [clojure.core.matrix.macros :refer [c-for]]
            [tech.compute.tensor :as ct]
            [tech.compute.cpu.tensor-math.writers :as writers]
            [tech.compute.cpu.tensor-math.binary-op-impls :as bin-impls]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defmacro ^:private custom-binary-accum-constant!-impl
  [datatype]
  (let [jvm-datatype (unsigned/datatype->jvm-datatype datatype)]
    `(fn [dest# dest-dims# dest-alpha#
          scalar#
          n-elems#
          custom-op#
          reverse-operation?#]
       (let [n-elems# (long n-elems#)
             dest# (item->typed-nio-buffer ~datatype dest#)
             dest-idx->address# (get-elem-dims->address dest-dims#
                                                        (get dest-dims# :shape))
             max-shape# (max-shape-from-dimensions dest-dims#)
             scalar# (unsigned/datatype->cast-fn :ignored ~datatype scalar#)
             dest-alpha# (unsigned/datatype->cast-fn :ignored ~datatype dest-alpha#)
             writer# (writers/get-serial-writer ~jvm-datatype)
             converter# (bin-impls/make-converter-elide-modulators
                         ~datatype reverse-operation?# custom-op#
                         dest-alpha# (unsigned/datatype->cast-fn :ignored ~datatype 1)
                         (read-datatype-cast-fn
                          ~datatype
                          (b-get dest# (.idx_to_address dest-idx->address#
                                                        ~'idx)))
                         scalar#)]
         (writer# dest# dest-dims# max-shape# n-elems# converter#)))))


(defmacro make-custom-bin-op-constant-table
  []
  (->> (for [dtype all-datatypes]
         [[dtype :custom] `(custom-binary-accum-constant!-impl ~dtype)])
       (into {})))


(def custom-bin-op-constant-table (make-custom-bin-op-constant-table))


(def binary-accum-constant-table custom-bin-op-constant-table)


(defmacro ^:private custom-binary-accum!-impl
  [datatype]
  (let [jvm-datatype (unsigned/datatype->jvm-datatype datatype)]
    `(fn [dest# dest-dims# dest-alpha#
          y# y-dims# y-alpha#
          n-elems#
          custom-op#
          reverse-operands?#]
       (let [n-elems# (long n-elems#)
             max-shape# (max-shape-from-dimensions dest-dims# y-dims#)
             dest# (item->typed-nio-buffer ~datatype dest#)
             dest-idx->address# (get-elem-dims->address dest-dims# max-shape#)
             dest-alpha# (datatype->cast-fn ~datatype dest-alpha#)
             y# (item->typed-nio-buffer ~datatype y#)
             y-idx->address# (get-elem-dims->address y-dims# max-shape#)
             y-alpha# (datatype->cast-fn ~datatype y-alpha#)
             writer# (writers/get-serial-writer ~jvm-datatype)
             converter# (bin-impls/make-converter-elide-modulators
                         ~datatype reverse-operands?# custom-op#
                         dest-alpha# y-alpha#
                         (read-datatype-cast-fn
                          ~datatype
                          (b-get dest# (.idx_to_address dest-idx->address# ~'idx)))
                         (read-datatype-cast-fn
                          ~datatype
                          (b-get y# (.idx_to_address y-idx->address# ~'idx))))]
         (writer# dest# dest-dims# max-shape# n-elems# converter#)))))


(defmacro make-custom-bin-op-table
  []
  (->> (for [dtype all-datatypes]
         [[dtype :custom] `(custom-binary-accum!-impl ~dtype)])
       (into {})))


(def custom-bin-op-table (make-custom-bin-op-table))


(def binary-accum-table custom-bin-op-table)
