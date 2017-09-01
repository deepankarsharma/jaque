(ns jaque.terminal
  (:use jaque.noun)
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [<! put! alt! close! sub go go-loop chan timeout]]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import (java.io File)
           (net.frodwith.jaque
             JaqueScreen
             data.Atom
             data.Cell
             data.List
             data.Tape
             data.Tank
             data.Noun)
           (javax.sound.midi MidiSystem Synthesizer MidiChannel)
           (com.googlecode.lanterna
             input.KeyType
             screen.TerminalScreen
             terminal.ExtendedTerminal
             terminal.DefaultTerminalFactory
             TerminalPosition
             TextCharacter)))

(defn- index-str 
  ([s with] (map vector s with))
  ([s] (index-str s (range 0 (.length s)))))

(defn- scrub-control-chars [s]
  (string/replace s #"\x1b\[[0-9;]*[mG]" ""))

(defprotocol BlitSink
  (spin [this caption])
  (clr [this])
  (hop [this to-column])
  (line [this text])
  (scroll [this])
  (save [this path-seq content-bytes])
  (link [this url])
  (dimensions [this])
  (restore [this])
  (commit [this]))

(defprotocol BeltSource
  (read-belt [this]))

(extend-type JaqueScreen
  BeltSource 
  (read-belt [this]
    (let [stroke (.readInput this)
          stype  (.getKeyType stroke)
          belt   (cond (= stype KeyType/ArrowDown)  [:aro :d]
                       (= stype KeyType/ArrowLeft)  [:aro :l]
                       (= stype KeyType/ArrowRight) [:aro :r]
                       (= stype KeyType/ArrowUp)    [:aro :u]
                       (= stype KeyType/Backspace)  [:bac 0]
                       (= stype KeyType/Delete)     [:del 0]
                       (= stype KeyType/Enter)      [:ret 0]
                       (= stype KeyType/Character) 
                       (let [c (long (.getCharacter stroke))]
                         (cond (.isCtrlDown stroke) [:ctl c]
                               (.isAltDown stroke)  [:met c]
                               :else                [:txt c 0]))
                       :else nil)]
      (if (nil? belt)
        (recur this)
        (noun [:belt belt]))))
  BlitSink   
  (clr [this] (.clear this))
  (commit [this] (.refresh this))
  (link [this url] (doto this (line url) scroll))
  (hop [this col]
    (let [pos (.getCursorPosition this)
          scr ^JaqueScreen this]
      (set! (. scr lastHop) col)
      (.setCursorPosition this (.withColumn pos (- col (.stripChars ^JaqueScreen this))))))
  (save [this path-seq content-bytes]
    (with-open [out (io/output-stream (io/file (string/join File/pathSeparator path-seq)))]
      (.write out content-bytes)))
  (restore [this]
    (let [scr ^JaqueScreen this]
      (line this (.lastLine scr))
      (hop this (.lastHop scr))))
  (dimensions [this]
    (let [s (.getTerminalSize this)]
      [(.getColumns s) (.getRows s)]))
  (spin [this caption]
    (let [[cols rows] (dimensions this)
          row  (dec rows)
          spin (.getSpinChar ^JaqueScreen this)
          full (str spin \u00AB caption \u00BB)]
      (doseq [[c i] (index-str full)]
        (.setCharacter this i row (TextCharacter. c)))))
  (scroll [this]
    (let [bottom (dec (.getRows (.getTerminalSize this)))
          newpos (TerminalPosition. 0 bottom)]
      (doto this
        (.scrollLines 0 bottom 1)
        (.setCursorPosition newpos))))
  (line [this text]
    (let [[cols rows] (dimensions this)
          row  (dec rows)
          cln  (scrub-control-chars text)
          scr  ^JaqueScreen this
          len  (.length cln)]
      (doseq [[c i] (index-str cln)]
        (.setCharacter this i row (TextCharacter. c)))
      (doseq [i (range len cols)]
        (.setCharacter this i row (TextCharacter. \space)))
      (set! (. scr lastLine) text)
      (set! (. scr stripChars) (- (.length text) len)))))

(defn- make-lanterna []
  (let [f (DefaultTerminalFactory.)
        t (.createTerminal f)]
    (when (isa? t ExtendedTerminal)
      (.maximize t))
    (let [s (doto (JaqueScreen. t)
              (.startScreen)
              (.doResizeIfNecessary))
          [cols rows] (dimensions s)]
      (.setCursorPosition s (TerminalPosition. 0 (dec rows)))
      s)))

(defn- wall-seq [wall]
  (map #(Tape/toString %) (List. wall)))

(defn- tank-seq [width tank]
  (wall-seq (Tank/wash 0 width tank)))

(defn- handle-tank [sink tank]
  (log/debug (string/join \newline (tank-seq 80 tank)))
  (let [[cols _] (dimensions sink)]
    (doseq [string (tank-seq (long cols) tank)]
      (doto sink
        (line string)
        (scroll)))
    (commit sink)))

(defn- handle-beep [^MidiChannel c]
  (go
    (.noteOn c 67 200)
    (<! (timeout 100))
    (.noteOff c 67)))

(defn- blit-one [sink beep spin ovum]
  (let [tag (Atom/cordToString (.head ovum))
        data (.tail ovum)]
    (case tag
      "bee" (put! spin data)
      "bel" (put! beep :beep)
      "clr" (clr sink)
      "hop" (hop sink (Atom/expectLong data))
      "lin" (line sink (Tape/toString data))
      "mor" (scroll sink)
      "sav" (let [pax (List. (.head data))
                  pad (Atom/toByteArray (.tail data))]
              (save sink pax pad))
      "sag" (let [pax (List. (.head data))
                  pad (Atom/toByteArray (Atom/jam (.tail data)))]
              (save sink pax pad))
      "url" (link sink data)
      (log/warnf "unhandled blit: %s" tag))))

(defn- handle-egg [sink poke beep spin ^Cell ovum]
  (let [egg (.tail ovum)
        tag (Atom/cordToString (.head egg))]
    (case tag
      "init" (let [[rows cols] (dimensions sink)
                   wir  [0 :term :1 0]
                   blew (noun [wir :blew rows cols]) 
                   hail (noun [wir :hail 0])]
               (put! poke blew)
               (put! poke hail))
      "blit" (do (doseq [ovum (List. (.tail egg))]
                   (blit-one sink beep spin ovum))
                 (commit sink))
      "logo" (close! poke)
      (log/warnf "unhandled terminal effect: %s" tag))))

(defn- make-wire [id]
  (noun [0 :term id 0]))

(defn- spinup [sink ch]
  (go-loop [cap 0]
    (let [c (if (Atom/isZero cap)
              (<! ch)
              (alt! ch 
                    ([cap]
                     (if (nil? cap)
                       false
                       cap))
                    (timeout 500) 
                    (do (spin sink (Atom/cordToString cap))
                        (commit sink)
                        cap)))]
      (when c (recur c)))))

(defn- listen [source poke wire]
  (let [scr ^JaqueScreen source]
    (.start
      (Thread. #(loop []
                  (when (.active scr)
                    (let [belt (read-belt source)
                          ovum (noun [wire belt])]
                      (put! poke ovum))
                    (recur)))))))

(defn start [effects id tank poke]
  (let [beep    (chan)
        spin    (chan)
        eggs    (chan)
        wire    (make-wire id)
        sink    (make-lanterna)
        do-beep (partial handle-beep
                  (let [synth (doto (MidiSystem/getSynthesizer) .open)]
                    (aget (.getChannels synth) 0)))
        do-egg  (partial handle-egg sink poke beep spin)
        do-tank (partial handle-tank sink)]
    (sub effects wire eggs)
    (listen sink poke wire)
    (spinup sink spin)
    (go
      (loop []
        (when (alt! beep (do (do-beep)
                             true)
                    eggs ([egg] (and (not (nil? egg))
                                     (do (do-egg egg)
                                         true)))
                    tank ([tac] (and (not (nil? tac))
                                     (do (do-tank tac)
                                         true))))
          (recur)))
      (.shutdown sink)
      (close! tank)
      (close! beep)
      (close! spin))))