(ns user)


(comment

;;;  a little core async test
(defn foo []
  (let [ch (async/chan)]
    (go (loop [i 100]
          (when (pos? i)
            (<! (async/timeout 1))
            (when (>! ch i) ;; <-- stop if caller closed the channel
              (recur (dec i)))))
        ;; cleanup here
        (prn ::terminated))
    ch))

(defn test-foo []
  (when-not (memory/remember [:test])
    (let [ch (foo)]
      (memory/remember! [:test] true)
      (go (prn "got1" (<! ch))
          (prn "got2" (<! ch))
          (prn "got3" (<! ch))
          (async/close! ch)
          (memory/remember! [:test] false)
          ))))

  )
