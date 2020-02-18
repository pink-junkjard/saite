[{:ed4 {:label "Mix 1", :opts {:order :row, :eltsper 1, :size "auto", :wrapfn {:tid :ed4, :$split nil, :fn [quote editor-repl-tab], :layout :left-right, :ns my.code, :ed-out-order :first-last, :width "730px", :src "10\n\n\n(defn roundit [r & {:keys [places] :or {places 4}}]\n  (let [n (Math/pow 10.0 places)]\n    (-> r (* n) Math/round (/ n))))\n\n(defn log2 [x]\n  (let [ln2 (Math/log 2)]\n    (/ (Math/log x) ln2)))\n\n(def obsdist\n  (clj\n   (deref\n    (def obsdist\n      (let [obs [[0 9] [1 78] [2 305] [3 752] [4 1150] [5 1166]\n                 [6 899] [7 460] [8 644] [9 533] [10 504]]\n            totcnt (->> obs (mapv second) (apply +))\n            pdist (map (fn[[k cnt]] [k (double (/ cnt totcnt))]) obs)]\n        pdist)))))\n\n\n(take 10 (clj (mapv #(let [RE (it/KLD (->> obsdist (into {}))\n                                      (->> (p/binomial-dist 10 %)\n                                        (into {})))\n                           REtt (roundit RE)\n                           ptt (roundit % :places 2)]\n                       {:x % :y RE})\n                    (range 0.06 0.98 0.01))))\n\n(let [x (clj (log2 23.4))\n      y (clj (roundit x))]\n  (+ x y))\n\n[(log2 23.4) (roundit 3.141847263)]\n\n(def vec-from-clj [(clj (log2 23.4)) (clj (roundit 3.141847263))])", :out-height "700px", :eid "ed-ed4", :height "700px"}, :ed-out-order nil, :rgap "px", :cgap "px"}, :specs []}} {:check {:label "Mix 2", :opts {:order :row, :eltsper 1, :rgap "20px", :cgap "20px", :size "auto", :wrapfn {:tid :check, :$split 39.94140625, :fn [quote interactive-doc-tab], :ns doc.code, :ed-out-order :first-last, :width "730px", :src "(def xxx\n  (clj (->> (range 0.00005 0.9999 0.001)\n              (mapv (fn[p] {:x p,\n                            :y (- (- (* p (m/log2 p)))\n                                  (* (- 1 p) (m/log2 (- 1 p))))})))))\n(hc/xform\n ht/layer-chart :FID :f1 :VID :v1\n :LEFT '[[p {:style {:width \"100px\":min-width \"100px\"}}]]\n :TOP '[[gap :size \"100px\"]\n         [md {:style {:font-size \"16px\"}}\n          \"Entropy \"]]\n :TITLE \"Entropy (Unpredictability)\"\n :LAYER [(hc/xform ht/gen-encode-layer\n                   :MARK \"line\"\n                   :XTITLE \"Probability of event\" :YTITLE \"H(p)\")\n         (hc/xform ht/xrule-layer :AGG \"mean\")]\n :DATA (clj (->> (range 0.00005 0.9999 0.001)\n              (mapv (fn[p] {:x p,\n                            :y (- (- (* p (m/log2 p)))\n                                  (* (- 1 p) (m/log2 (- 1 p))))})))))\n", :out-height "100px", :eid "ed-check", :height "700px"}, :ed-out-order nil}, :specs []}}]