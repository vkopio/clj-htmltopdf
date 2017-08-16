(ns clj-htmltopdf.watermark
  (:import
    [java.io InputStream OutputStream]
    [org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream PDPageContentStream$AppendMode]
    [org.apache.pdfbox.pdmodel.common PDRectangle]
    [org.apache.pdfbox.pdmodel.font PDType1Font PDFont]
    [org.apache.pdfbox.pdmodel.graphics.state PDExtendedGraphicsState]
    [org.apache.pdfbox.util Matrix]))

; TODO: this is a temporary measure to allow at least _some_ font customizability for watermarks
;       until something more comprehensive can be implemented such as allowing loading of external
;       TTF fonts via PDTrueTypeFont.loadTTF()
(def watermark-fonts
  {"times-roman"           PDType1Font/TIMES_ROMAN
   "times-bold"            PDType1Font/TIMES_BOLD
   "times-italic"          PDType1Font/TIMES_ITALIC
   "times-bolditalic"      PDType1Font/TIMES_BOLD_ITALIC
   "helvetica"             PDType1Font/HELVETICA
   "helvetica-bold"        PDType1Font/HELVETICA_BOLD
   "helvetica-oblique"     PDType1Font/HELVETICA_OBLIQUE
   "helvetica-boldoblique" PDType1Font/HELVETICA_BOLD_OBLIQUE
   "courier"               PDType1Font/COURIER
   "courier-bold"          PDType1Font/COURIER_BOLD
   "courier-oblique"       PDType1Font/COURIER_OBLIQUE
   "courier-boldoblique"   PDType1Font/COURIER_BOLD_OBLIQUE})

(defn render-watermark!
  [^PDPage page ^PDPageContentStream cs options]
  (let [font          (or (get watermark-fonts (:font options))
                          (get watermark-fonts "helvetica-bold"))
        font-size     (float (or (:font-size options) 36.0))
        font-color    (or (:color options) [0 0 0])
        text          (:text options)
        text-width    (/ (* (.getStringWidth font text) font-size) 1000.0)
        text-height   (/ (* (.getHeight (.getFontBoundingBox (.getFontDescriptor ^PDFont font))) font-size) 1000.0)
        rotation      (float (or (:rotation options) 0))
        page-size     (.getMediaBox page)
        page-width    (.getWidth page-size)
        page-height   (.getHeight page-size)
        x             (if (= :center (:x options)) (/ page-width 2) (float (:x options)))
        y             (if (= :center (:y options)) (/ page-height 2) (float (:y options)))
        transform     (doto (Matrix.)
                        (.translate x y)
                        (.rotate (Math/toRadians rotation))
                        (.translate (- (/ text-width 2)) (- (/ text-height 2))))]
    (when (:opacity options)
      (let [r0 (PDExtendedGraphicsState.)]
        (.setNonStrokingAlphaConstant r0 (float (:opacity options)))
        (.setGraphicsStateParameters cs r0)))
    (.beginText cs)
    (.setFont cs font font-size)
    (.setNonStrokingColor cs (int (nth font-color 0)) (int (nth font-color 1)) (int (nth font-color 2)))
    (.setTextMatrix cs transform)
    (.showText cs text)
    (.endText cs)))

(defn write-watermark!
  [^InputStream pdf ^OutputStream out {:keys [watermark] :as options}]
  (with-open [doc (PDDocument/load pdf)]
    (doseq [^PDPage page (.getPages doc)]
      (let [cs (PDPageContentStream. doc page PDPageContentStream$AppendMode/APPEND true true)]
        (with-open [cs cs]
          (if (map? watermark)
            (render-watermark! page cs watermark)
            (watermark page cs)))))
    (.save doc out)
    out))
