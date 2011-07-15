(ns doom)

(def num-players 2)
(def max-dice 3)
(def board-size 3)
(def board-hexnum (* board-size board-size))

(defn third [coll]
  (first (rest (rest coll))))

(defn indexed [coll]
  (map list (range) coll))

(defn gen-board []
  (let [f (fn [] [(rand-int num-players) (inc (rand-int max-dice))])]
    (vec (repeatedly board-hexnum f))))

(defn player-letter [n]
  (char (+ 97 n)))

(defn draw-board [board]
  (doseq [row (range board-size)]
    (print (apply str (repeat (- board-size row) \space)))
    (doseq [col (range board-size)]
      (let [[player dice] (board (+ col (* board-size row)))]
        (print (str (player-letter player) "-" dice " "))))
    (newline)))

(defn neighbors [pos]
  (let [up (- pos board-size)
        down (+ pos board-size)]
    (for [p (concat [up down]
                    (when-not (zero? (mod pos board-size))
                      [(dec up) (dec pos)])
                    (when-not (zero? (mod (inc pos) board-size))
                      [(inc pos) (inc down)]))
          :when (< -1 p board-hexnum)]
      p)))

(def neighbors (memoize neighbors))

(defn board-attack [board player src dst dice]
  (assoc board
    src [player 1]
    dst [player (dec dice)]))

(defn add-new-dice [board player spare-dice]
  (letfn [(f [[[cur-player cur-dice] :as lst] n acc]
            (cond (zero? n) (into acc lst)
                  (empty? lst) acc
                  :else (if (and (= cur-player player)
                                 (< cur-dice max-dice))
                          (recur (rest lst)
                                 (dec n)
                                 (conj acc [cur-player (inc cur-dice)]))
                          (recur (rest lst)
                                 n
                                 (conj acc [cur-player cur-dice])))))]
    (f board spare-dice [])))

(declare game-tree add-passing-move attacking-moves)

(defn game-tree [board player spare-dice first-move?]
  [player
   board
   (add-passing-move board
                     player
                     spare-dice
                     first-move?
                     (attacking-moves board player spare-dice))])

(def game-tree (memoize game-tree))

(defn add-passing-move [board player spare-dice first-move? moves]
  (if first-move?
    moves
    (cons [nil
           (game-tree (add-new-dice board player (dec spare-dice))
                      (mod (inc player) num-players)
                      0
                      true)]
          moves)))

(defn attacking-moves [board cur-player spare-dice]
  (let [player (fn [pos] (first (board pos)))
        dice (fn [pos] (second (board pos)))]
    (mapcat
     (fn [src]
       (when (= cur-player (player src))
         (mapcat
          (fn [dst]
            (when (and (not= cur-player (player dst))
                       (> (dice src) (dice dst)))
              [[[src dst]
                (game-tree (board-attack board
                                         cur-player
                                         src
                                         dst
                                         (dice src))
                           cur-player
                           (+ spare-dice (dice dst))
                           false)]]))
          (neighbors src))))
     (range board-hexnum))))

(defn print-info [tree]
  (println "current player =" (player-letter (first tree)))
  (draw-board (second tree)))

(defn handle-human [tree]
  (println "choose your move:")
  (let [print-moves (fn print-moves [moves n]
                      (when (seq moves)
                        (let [move (first moves)
                              action (first move)]
                          (print (str n ". "))
                          (if action
                            (println (first action) "->" (second action))
                            (println "end turn"))
                          (recur (rest moves) (inc n)))))
        moves (third tree)]
    (print-moves moves 1)
    (second (nth moves (dec (read))))))

(defn winners [board]
  (let [tally (map first board)
        totals (frequencies tally)
        best (apply max (vals totals))]
    (map first (filter (fn [[player total]] (= best total)) totals))))

(defn announce-winners [board]
  (let [ws (winners board)]
    (if (> (count ws) 1)
      (println (str "The winners are " (map player-letter ws) "!"))
      (println (str "The winner is " (player-letter (first ws)) "!")))))

(defn play-vs-human [tree]
  (print-info tree)
  (if (seq (third tree))
    (recur (handle-human tree))
    (announce-winners (second tree))))

(declare rate-position get-ratings)

(defn rate-position [tree player]
  (let [moves (third tree)]
    (if (seq moves)
      (if (= player (first tree))
        (apply max (get-ratings tree player))
        (apply min (get-ratings tree player)))
      (let [w (winners (second tree))]
        (if (some #{player} w)
          (/ 1 (count w))
          0)))))

(def rate-position (memoize rate-position))

(defn get-ratings [tree player]
  (map #(rate-position (second %) player) (third tree)))

(defn handle-computer [tree]
  (let [ratings (get-ratings tree (first tree))
        idx-best (first (reduce (fn [x y]
                                  (if (>= (second x) (second y))
                                    x
                                    y))
                                (indexed ratings)))]
    (second (nth (third tree) idx-best))))

(defn play-vs-computer [tree]
  (print-info tree)
  (cond (empty? (third tree)) (announce-winners (second tree))
        (zero? (first tree)) (recur (handle-human tree))
        :else (recur (handle-computer tree))))
