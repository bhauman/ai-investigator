(ns triage.core
  "CLI entry point for triage swarm"
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [triage.evaluator :as eval]
            [triage.investigators :as inv]))

;; ============================================================================
;; CLI options
;; ============================================================================

(def cli-options
  [["-t" "--timeout MILLISECONDS" "Investigation timeout in milliseconds (default: 600000)"
    :default 600000
    :parse-fn parse-long
    :validate [#(> % 0) "Must be a positive number"]]
   ["-e" "--eval-timeout MILLISECONDS" "Evaluation timeout in milliseconds (default: 600000)"
    :default 600000
    :parse-fn parse-long
    :validate [#(> % 0) "Must be a positive number"]]
   ["-v" "--verbose" "Print progress messages"]
   ["-i" "--investigations-only" "Run investigations only (skip evaluation)"]
   ["-h" "--help" "Show this help message"]])

(defn usage [options-summary]
  (str/join \newline
            ["triage-swarm - Run parallel AI investigations and synthesize results"
             ""
             "Usage: triage-swarm [options] PROMPT"
             "       echo PROMPT | triage-swarm [options]"
             ""
             "Options:"
             options-summary
             ""
             "Description:"
             "  Runs Claude, Gemini, and Codex in parallel to investigate a problem."
             "  Each investigator operates in read-only mode (no file modifications)."
             "  Results are synthesized by a Claude evaluator that produces:"
             "    - Primary path recommendation"
             "    - Backup path"
             "    - Verification steps"
             "    - Implementation plan"
             ""
             "Examples:"
             "  # Investigate a bug"
             "  triage-swarm \"Why is the API returning 500 errors on /users?\""
             ""
             "  # With verbose output"
             "  triage-swarm -v \"Analyze the authentication flow\""
             ""
             "  # Run investigations only (no evaluation)"
             "  triage-swarm -i \"Search for memory leaks\""
             ""
             "  # From stdin"
             "  echo \"Fix the login bug\" | triage-swarm"]))

(defn error-msg [errors]
  (str "Error parsing command line:\n\n"
       (str/join \newline errors)))

;; ============================================================================
;; Stdin detection
;; ============================================================================

(defn has-stdin-data?
  "Check if stdin has data available (not a TTY)."
  []
  (try
    (.ready *in*)
    (catch Exception _ false)))

(defn get-prompt
  "Get prompt from command-line arguments or stdin."
  [arguments]
  (cond
    (seq arguments) (str/join " " arguments)
    (has-stdin-data?) (str/trim (slurp *in*))
    :else nil))

;; ============================================================================
;; Output formatting
;; ============================================================================

(defn print-investigation-summary
  "Print a summary of investigation results"
  [results]
  (println "\n=== INVESTIGATION SUMMARY ===\n")
  (doseq [{:keys [investigator exit-code duration-ms]} results]
    (println (format "  %s: %s (%.1fs)"
                     (str/upper-case (name investigator))
                     (if (zero? exit-code) "OK" "FAILED")
                     (/ duration-ms 1000.0))))
  (println))

(defn print-full-investigations
  "Print full investigation outputs"
  [results]
  (println "\n=== INVESTIGATION REPORTS ===\n")
  (println (inv/format-all-results results)))

(defn print-evaluation
  "Print evaluation result"
  [evaluation]
  (println "\n=== EVALUATION ===\n")
  (if (zero? (:exit-code evaluation))
    (println (:output evaluation))
    (do
      (println "Evaluation failed:")
      (println (:error evaluation))))
  (println))

;; ============================================================================
;; Main
;; ============================================================================

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      (println (usage summary))

      errors
      (do
        (binding [*out* *err*]
          (println (error-msg errors))
          (println)
          (println (usage summary)))
        (System/exit 1))

      :else
      (let [prompt (get-prompt arguments)]
        (if-not prompt
          (do
            (binding [*out* *err*]
              (println "Error: No prompt provided")
              (println "Provide a prompt as an argument or via stdin")
              (println)
              (println (usage summary)))
            (System/exit 1))

          (if (:investigations-only options)
            ;; Investigations only mode
            (do
              (when (:verbose options)
                (println "Running investigations in parallel...")
                (flush))
              (let [results (inv/run-investigators-parallel
                              prompt
                              :timeout-ms (:timeout options))]
                (print-investigation-summary results)
                (print-full-investigations results)))

            ;; Full triage mode
            (let [result (eval/run-triage
                           prompt
                           :investigation-timeout-ms (:timeout options)
                           :evaluation-timeout-ms (:eval-timeout options)
                           :verbose (:verbose options))]
              (when (:verbose options)
                (print-investigation-summary (:investigations result)))
              (print-evaluation (:evaluation result)))))))))
