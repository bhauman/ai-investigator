(ns triage.investigators
  "Parallel LLM invocation for read-only investigation"
  (:require [babashka.process :as p]
            [clojure.string :as str]))

;; ============================================================================
;; Read-only system prompts
;; ============================================================================

(def read-only-prefix
  "You are a read-only investigator. DO NOT make any changes.
Only analyze, search, read files, and provide findings.
Do not use Edit, Write, or any modification tools.
Focus on understanding the problem and proposing solutions.

TASK:
")

;; ============================================================================
;; Individual investigator invocations
;; ============================================================================

(defn invoke-claude
  "Invoke Claude CLI in non-interactive mode.
   Returns map with :investigator, :output, :exit-code, :duration-ms"
  [prompt & {:keys [timeout-ms] :or {timeout-ms 600000}}]
  (let [full-prompt (str read-only-prefix prompt)
        start-time (System/currentTimeMillis)
        result (try
                 (p/shell {:out :string
                           :err :string
                           :continue true
                           :in ""}
                          "claude"
                          "--permission-mode" "plan"
                          "-p" full-prompt)
                 (catch Exception e
                   {:out ""
                    :err (str "Failed to invoke claude: " (.getMessage e))
                    :exit -1}))
        end-time (System/currentTimeMillis)]
    {:investigator :claude
     :output (str/trim (:out result))
     :error (:err result)
     :exit-code (:exit result)
     :duration-ms (- end-time start-time)}))

(defn invoke-gemini
  "Invoke Gemini CLI in one-shot mode.
   Returns map with :investigator, :output, :exit-code, :duration-ms"
  [prompt & {:keys [timeout-ms] :or {timeout-ms 600000}}]
  (let [full-prompt (str read-only-prefix prompt)
        start-time (System/currentTimeMillis)
        result (try
                 (p/shell {:out :string
                           :err :string
                           :continue true
                           :in ""}
                          "gemini" "-s" full-prompt)
                 (catch Exception e
                   {:out ""
                    :err (str "Failed to invoke gemini: " (.getMessage e))
                    :exit -1}))
        end-time (System/currentTimeMillis)]
    {:investigator :gemini
     :output (str/trim (:out result))
     :error (:err result)
     :exit-code (:exit result)
     :duration-ms (- end-time start-time)}))

(defn invoke-codex
  "Invoke Codex CLI in non-interactive mode.
   Returns map with :investigator, :output, :exit-code, :duration-ms"
  [prompt & {:keys [timeout-ms] :or {timeout-ms 600000}}]
  (let [full-prompt (str read-only-prefix prompt)
        start-time (System/currentTimeMillis)
        result (try
                 (p/shell {:out :string
                           :err :string
                           :continue true
                           :in ""}
                          "codex" "exec" "--sandbox" "read-only" "--skip-git-repo-check" full-prompt)
                 (catch Exception e
                   {:out ""
                    :err (str "Failed to invoke codex: " (.getMessage e))
                    :exit -1}))
        end-time (System/currentTimeMillis)]
    {:investigator :codex
     :output (str/trim (:out result))
     :error (:err result)
     :exit-code (:exit result)
     :duration-ms (- end-time start-time)}))

;; ============================================================================
;; Parallel execution
;; ============================================================================

(defn run-investigators-parallel
  "Run all three investigators in parallel.
   Returns vector of results: [{:investigator :claude ...} {:investigator :gemini ...} {:investigator :codex ...}]"
  [prompt & {:keys [timeout-ms] :or {timeout-ms 600000}}]
  (let [investigators [invoke-claude invoke-gemini invoke-codex]
        results (pmap #(% prompt :timeout-ms timeout-ms) investigators)]
    (vec results)))

;; Anonymized names for evaluator (avoids model bias)
(def investigator-aliases
  {:claude "Alice"
   :gemini "Bob"
   :codex  "Carol"})

(defn format-investigator-result
  "Format a single investigator result for display (uses real names)"
  [{:keys [investigator output error exit-code duration-ms]}]
  (str "## " (str/upper-case (name investigator)) " Investigation\n"
       "Duration: " (format "%.1f" (/ duration-ms 1000.0)) "s | "
       "Exit: " exit-code "\n\n"
       (if (zero? exit-code)
         output
         (str "ERROR: " error "\n" output))
       "\n"))

(defn format-investigator-result-anonymous
  "Format a single investigator result with anonymized name (for evaluator)"
  [{:keys [investigator output error exit-code duration-ms]}]
  (let [alias (get investigator-aliases investigator "Unknown")]
    (str "## " alias "'s Investigation\n"
         "Duration: " (format "%.1f" (/ duration-ms 1000.0)) "s | "
         "Exit: " exit-code "\n\n"
         (if (zero? exit-code)
           output
           (str "ERROR: " error "\n" output))
         "\n")))

(defn format-all-results
  "Format all investigator results for display (uses real names)"
  [results]
  (str/join "\n---\n\n" (map format-investigator-result results)))

(defn format-all-results-anonymous
  "Format all investigator results with anonymized names (for evaluator)"
  [results]
  (str/join "\n---\n\n" (map format-investigator-result-anonymous results)))
