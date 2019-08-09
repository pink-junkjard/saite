(ns  aerial.saite.core
  (:require
   [cljs.core.async
    :as async
    :refer (<! >! put! chan)
    :refer-macros [go go-loop]]
   [clojure.string :as cljstr]

   [aerial.hanami.core
    :as hmi
    :refer [printchan user-msg
            re-com-xref xform-recom
            default-header-fn start
            update-adb get-adb
            get-vspec update-vspecs
            get-tab-field add-tab update-tab-field
            add-to-tab-body remove-from-tab-body
            active-tabs
            md vgl app-stop]]
   [aerial.hanami.common
    :as hc
    :refer [RMV]]

   [aerial.saite.codemirror
    :as cm
    :refer [code-mirror cm]]
   [aerial.saite.tabs
    :refer [editor-repl-tab interactive-doc-tab extns-xref
            file-modal tab-box tab<->]]
   [aerial.saite.savrest
    :refer [get-tab-data xform-tab-data load-doc]]

   [cljsjs.mathjax]

   [com.rpl.specter :as sp]

   [reagent.core :as rgt]

   [re-com.core
    :as rcm
    :refer [h-box v-box box border gap line h-split v-split scroller
            button row-button md-icon-button md-circle-icon-button info-button
            input-text input-password input-textarea
            label title p
            single-dropdown
            checkbox radio-button slider progress-bar throbber
            horizontal-bar-tabs vertical-bar-tabs
            modal-panel popover-content-wrapper popover-anchor-wrapper]
    :refer-macros [handler-fn]]
   [re-com.box
    :refer [flex-child-style]]
   [re-com.dropdown
    :refer [filter-choices-by-keyword single-dropdown-args-desc]]

   ))




(defn react-hack
  "Monkey patching ... sigh ... This changes the (incorrect) behavior of
  React's removeChild and insertBefore functions which do not properly
  track changes in the DOM as effected by 3rd party libs. MathJax,
  Google Translate, etc. For details see
  `https://github.com/facebook/react/issues/11538`"
  []
  (when (and (fn? js/Node) js/Node.prototype)
    (let [orig-rm-child Node.prototype.removeChild
          new-rm-child
          (fn [child]
            (this-as this
              (if (not= child.parentNode this)
                (do (when js/console
                      (js/console.error
                       "Cannot remove a child from a different parent"
                       child this))
                    child)
                (do #_(printchan this (js-arguments))
                    (.apply orig-rm-child this (js-arguments))))))

          orig-ins-b4 Node.prototype.insertBefore
          new-ins-b4 (fn [newNode referenceNode]
                       (this-as this
                         (if (and referenceNode
                                  (not= referenceNode.parentNode this))
                           (do (when js/console
                                 (js/console.error
                                  "Cannot insert before a reference node "
                                  "from a different parent" referenceNode this))
                               newNode)
                           (do #_(printchan this (js-arguments))
                               (.apply orig-ins-b4 this (js-arguments))))))]
      (set! Node.prototype.removeChild new-rm-child)
      (set! Node.prototype.insertBefore new-ins-b4))))

(react-hack)




;;; Header ================================================================ ;;;


(defn saite-header []
  (let[show? (rgt/atom false)
       session-name (rgt/atom "")
       file-name (rgt/atom "")
       choices (rgt/atom nil)
       mode (rgt/atom nil)
       donefn (fn[event]
                (go (async/>! (get-adb [:main :com-chan])
                              {:session @session-name :file @file-name}))
                (reset! show? false))
       cancelfn (fn[event]
                  (go (async/>! (get-adb [:main :com-chan]) :cancel))
                  (reset! show? false))]
    (fn []
      [h-box :gap "10px" :max-height "30px"
       :children
       [[gap :size "5px"]
        [:img {:src (get-adb [:main :logo])}]
        [hmi/session-input]
        [gap :size "5px"]
        [title
         :level :level3
         :label [:span.bold (get-adb [:main :uid :name])]]
        [gap :size "30px"]
        [border :padding "2px" :radius "2px"
         :l-border "1px solid lightgrey"
         :r-border "1px solid lightgrey"
         :b-border "1px solid lightgrey"
         :child
         [h-box :gap "10px"
          :children [[md-circle-icon-button
                      :md-icon-name "zmdi-upload" :size :smaller
                      :tooltip "Upload Document"
                      :on-click
                      #(go (let [ch (get-adb [:main :com-chan])]
                             (js/console.log "upload clicked")
                             (reset! session-name (get-adb [:main :uid :name]))
                             (reset! file-name (get-adb [:main :files :load]))
                             (reset! mode :load)
                             (reset! show? true)
                             (let [location (async/<! ch)]
                               (when (not= :cancel location)
                                 (let [fname (location :file)
                                       location (assoc
                                                 location
                                                 :file (str fname ".clj"))
                                       uid (get-adb [:main :uid])]
                                   (update-adb [:main :files :load] fname)
                                   (hmi/send-msg
                                    {:op :load-data
                                     :data {:uid uid
                                            :location location}}))))))]

                     [md-circle-icon-button
                      :md-icon-name "zmdi-download" :size :smaller
                      :tooltip "Save Document"
                      :on-click
                      #(go (let [ch (get-adb [:main :com-chan])]
                             (js/console.log "download clicked")
                             (reset! session-name (get-adb [:main :uid :name]))
                             (reset! file-name (get-adb [:main :files :save]))
                             (reset! mode :save)
                             (reset! show? true)
                             (let [location (async/<! ch)]
                               (when (not= :cancel location)
                                 (let [fname (location :file)
                                       location (assoc
                                                 location
                                                 :file (str fname ".clj"))]
                                   (update-adb [:main :files :save] fname)
                                   (let [spec-info (xform-tab-data
                                                    (get-tab-data))]
                                     (hmi/send-msg
                                      {:op :save-data
                                       :data {:loc location
                                              :info spec-info}})))))))]

                     (when @show?
                       (when (nil? @choices)
                         (reset! choices ((get-adb [:main :files]) :choices)))
                       [file-modal choices session-name file-name mode
                        donefn cancelfn])] ]]

        [gap :size "20px"]
        [tab-box]]])))




;;; Messaging ============================================================ ;;;


(defmethod user-msg :data [msg]
  (printchan :DATA msg))


(def newdoc-data (atom []))

(defmethod user-msg :load-data [msg]
  (let [data (msg :data)]
    (reset! newdoc-data data)
    (load-doc data extns-xref)))


(defmethod user-msg :clj-read [msg]
  (let [data (msg :data)
        render? (data :render?)
        clj (dissoc data :render?)
        result (if render?
                 clj
                 (try (-> clj clj->js (js/JSON.stringify nil, 2))
                      (catch js/Error e (str e))))
        ch (get-adb [:main :convert-chan])]
    #_(printchan render? result)
    (go (async/>! ch result))))


(defmethod user-msg :app-init [msg]
  (let [{:keys [save-info editor]} (msg :data)]
    (printchan :APP-INIT save-info editor)
    (update-adb [:main :convert-chan] (async/chan)
                [:main :com-chan] (async/chan)

                [:main :files :choices] (into {} save-info)
                [:main :files :save] "session.clj"
                [:main :files :load] "session.clj"

                [:main :editor] editor
                [:editors] {})

    (add-tab {:id :xvgl
              :label "<->"
              :opts {:extfn (tab<-> :NA)}})))


(defn update-data
  "Originally meant as general updater of vis plot/chart data
  values. But to _render_ these, requires knowledge of the application
  pages/structure. So, this is not currently used. If we can figure
  out Vega chageSets and how they update, we may be able to make this
  a general op in Hanami."
  [data-maps]
  (printchan :UPDATE-DATA data-maps)
  #_(mapv (fn [{:keys [usermeta data]}]
          (let [vid (usermeta :vid)
                spec (dissoc (get-vspec vid) :data)]
            (assoc-in spec [:data :values] data)))
        data-maps))

(defmethod user-msg :data [msg]
  (update-data (msg :data)))




;;; Call Backs ============================================================ ;;;

#_(js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub])
#_(js/MathJax.Hub.Queue
   #js ["Typeset" js/MathJax.Hub
        (js/document.getElementById (get-adb [:mathjax]))])

(def mathjax-chan (async/chan 10))

(defn mathjax-put [fid]
  (go (async/>! mathjax-chan (name fid))))

(go-loop [id (async/<! mathjax-chan)]
  (let [elt (js/document.getElementById id)
        _ (printchan :ID id, :ELT elt)
        jsvec (when elt (clj->js ["Typeset" js/MathJax.Hub elt]))]
    (async/<! (async/timeout 50))
    (cond
      ::global (js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub])
      jsvec    (js/MathJax.Hub.Queue jsvec)
      :else    (async/>! mathjax-chan id)))
  (recur (async/<! mathjax-chan)))


(defn frame-callback
  ([]
   (go (async/>! mathjax-chan ::global)))
  ([spec frame] #_(printchan :FRAME-CALLBACK :spec spec)
   (let [id (frame :frameid)]
     (go (async/>! mathjax-chan id))
     [spec frame])))


(defn symxlate-callback [sym]
  (let [snm (name sym)]
    (cond ;; (= snm "md") md
          (= snm "cm") (cm)
          :else sym)))




;;; Instrumentors ========================================================= ;;;

(defn bar-slider-fn [tid val]
  (let [tabval (get-tab-field tid)
        spec-frame-pairs (tabval :spec-frame-pairs)]
    (printchan "Slider update " val)
    (update-tab-field tid :compvis nil)
    (update-tab-field
     tid :spec-frame-pairs
     (mapv (fn[[spec frame]]
             (let [cljspec spec
                   data (mapv (fn[m] (assoc m :b (+ (m :b) val)))
                              (get-in cljspec [:data :values]))
                   newspec (assoc-in cljspec [:data :values] data)]
               [newspec frame]))
           spec-frame-pairs))))

(defn instrumentor [{:keys [tabid spec]}]
  (printchan "Test Instrumentor called" :TID tabid #_:SPEC #_spec)
  (let [cljspec spec
        udata (cljspec :usermeta)]
    (update-adb [:udata] udata)

    (cond
      (not (map? udata)) []

      (udata :slider)
      (let [sval (rgt/atom "0.0")]
        (printchan :SLIDER-INSTRUMENTOR)
        {:top (xform-recom
               (udata :slider)
               246 -10.0
               :m1 sval
               :oc1 #(do (bar-slider-fn tabid %)
                         (reset! sval (str %)))
               :oc2 #(do (bar-slider-fn tabid (js/parseFloat %))
                         (reset! sval %)))})

      :else {}
      )))




;;; Startup ============================================================== ;;;


#_(when-let [elem (js/document.querySelector "#app")]
  (hc/update-defaults
   :USERDATA {:tab {:id :TID, :label :TLBL, :opts :TOPTS}
              :frame {:top :TOP, :bottom :BOTTOM,
                      :left :LEFT, :right :RIGHT
                      :fid :FID}
              :opts :OPTS
              :vid :VID,
              :msgop :MSGOP,
              :session-name :SESSION-NAME}
   :MSGOP :tabs, :SESSION-NAME "Exploring"
   :TID :expl1, :TLBL #(-> :TID % name cljstr/capitalize)
   :OPTS (hc/default-opts :vgl), :TOPTS (hc/default-opts :tab))
  (start :elem elem
         :port js/location.port
         :symxlate-cb symxlate-callback
         :frame-cb frame-callback
         :header-fn saite-header
         :instrumentor-fn instrumentor))



(comment

  (when-let [elem (js/document.querySelector "#app")]
    (hc/update-defaults
     :USERDATA {:tab {:id :TID, :label :TLBL, :opts :TOPTS}
                :frame {:top :TOP, :bottom :BOTTOM,
                        :left :LEFT, :right :RIGHT
                        :fid :FID}
                :opts :OPTS
                :vid :VID,
                :msgop :MSGOP,
                :session-name :SESSION-NAME}
     :MSGOP :tabs, :SESSION-NAME "Exploring"
     :TID :expl1, :TLBL #(-> :TID % name cljstr/capitalize)
     :OPTS (hc/default-opts :vgl), :TOPTS (hc/default-opts :tab))
    (start :elem elem
           :port 3000
           :symxlate-cb symxlate-callback
           :frame-cb frame-callback
           :header-fn saite-header
           :instrumentor-fn instrumentor))







  (->> newdoc-data deref
       (mapv (fn[m]
               (let [tm (->> m vals first)]
                 (if-let [info (->> tm :opts :wrapfn)]
                   (let [f (-> info :fn second)
                         {:keys [tid src]} info
                         label (tm :label)
                         specs (tm :specs)
                         args (->> (dissoc info :fn :tid :src)
                                   seq (cons [:specs specs])
                                   (apply concat))]
                     `(apply ~f ~tid ~label ~src ~args))
                   m)))))





  (let[frame (js/document.getElementById "f1")]
    (js/console.log (aget frame.childNodes 2)))

  (let[frame (js/document.getElementById "f1")
       bottom (aget frame.childNodes 4)]
    (js/console.log bottom.childNodes))

  (let [children `[h-box [md "### title"] [p "some text"]]]
    (js/console.log (rgt/as-element children)))

  (let [children `[h-box [md "### title"] [p "some text"]]
        div (js/document.createElement "div")]
    (rgt/render children div)
    (js/console.log (aget div.childNodes 0)))




  (def loaded? (r/atom false))

  (.addEventListener js/document "load" #(reset! loaded? true))

  (defn app []
    [:div {:class (when @loaded? "class-name")}])




  (def CM-VALS
    (sp/recursive-path
     [] p
     (sp/cond-path #(and (vector? %)
                         (and (-> % first symbol?)
                              (= (-> % first name) "cm")))
                   sp/STAY
                   vector? [sp/ALL p])))

  (let [hiccup (sp/transform
                [sp/ALL CM-VALS]
                (fn[cm]
                  (let [m (->> cm rest (partition-all 2) (mapv vec) (into {}))
                        id (m :id)
                        ed (get-adb [:editors id])
                        instg (deref (ed :in))
                        otstg (deref (ed :ot))
                        opts (merge (ed :opts) {:instg instg :otstg otstg} m)]
                    [(-> cm first name symbol) ::opts opts]))
                '[[aerial.saite.examples/gap :size "10px"]
                  [aerial.saite.examples/cm :id "cm-scatter-1"]])]
    [(->> hiccup second first (= 'cm))
     (->> hiccup second second (= ::opts))])

  (sp/select [sp/ALL CM-VALS] '[[aerial.saite.examples/gap :size "10px"]
                                [aerial.saite.examples/cm :id "cm-scatter-1"]])

  cljs.reader/read-string

  (def paratxt2 (js/document.createTextNode "\\(f(x) = \\sqrt x \\)"))
  (def para (js/document.createElement "P"))
  (.appendChild para paratxt2)
  (do
    (.replaceChild divelt para (aget divelt.childNodes 0))
    (js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub para]))

  (js/document.createElement "a")

  )
