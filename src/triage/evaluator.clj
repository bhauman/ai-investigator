(ns triage.evaluator
  "Claude evaluator for synthesizing investigator outputs"
  (:require [babashka.process :as p]
            [clojure.string :as str]
            [triage.investigators :as inv]))

;; ============================================================================
;; Evaluator prompt template
;; ============================================================================

(def evaluator-system-prompt
  "You are a senior software architect evaluating investigation reports from three AI assistants (Claude, Gemini, and Codex). Each assistant investigated the same problem independently.

Your task is to synthesize their findings and produce:

1. **PRIMARY PATH**: Choose the best approach from the investigations. Explain why this approach is preferred.

2. **BACKUP PATH**: Identify an alternative approach in case the primary fails. Explain when to switch to this.

3. **VERIFICATION STEPS**: List specific checks to validate the solution works:
   - What tests to run
   - What behavior to verify
   - What edge cases to check

4. **IMPLEMENTATION PLAN**: Provide a step-by-step plan:
   - Each step should be concrete and actionable
   - Include file paths when known
   - Note any dependencies between steps
   - Estimate complexity (simple/medium/complex) for each step

Be concise but thorough. Focus on actionable guidance.")

(defn build-evaluator-prompt
  "Build the full prompt for the evaluator including all investigation results"
  [original-prompt results]
  (str "# Original Task\n\n"
       original-prompt
       "\n\n"
       "# Investigation Reports\n\n"
       (inv/format-all-results results)
       "\n\n"
       "# Your Evaluation\n\n"
       "Please synthesize the above investigations and provide:\n"
       "1. PRIMARY PATH - Best approach and rationale\n"
       "2. BACKUP PATH - Alternative approach\n"
       "3. VERIFICATION STEPS - How to confirm the fix works\n"
       "4. IMPLEMENTATION PLAN - Step-by-step instructions"))

;; ============================================================================
;; Evaluator invocation
;; ============================================================================

(defn invoke-evaluator
  "Run Claude as the evaluator to synthesize investigation results.
   Returns map with :output, :exit-code, :duration-ms"
  [original-prompt results & {:keys [timeout-ms] :or {timeout-ms 600000}}]
  (let [full-prompt (build-evaluator-prompt original-prompt results)
        start-time (System/currentTimeMillis)
        result (try
                 (p/shell {:out :string
                           :err :string
                           :continue true
                           :in ""}
                          "claude"
                          "--system-prompt" evaluator-system-prompt
                          "-p" full-prompt)
                 (catch Exception e
                   {:out ""
                    :err (str "Failed to invoke evaluator: " (.getMessage e))
                    :exit -1}))
        end-time (System/currentTimeMillis)]
    {:output (str/trim (:out result))
     :error (:err result)
     :exit-code (:exit result)
     :duration-ms (- end-time start-time)}))

;; ============================================================================
;; Full triage pipeline
;; ============================================================================

(defn run-triage
  "Run the complete triage pipeline:
   1. Run investigators in parallel
   2. Feed results to evaluator
   3. Return combined results

   Options:
   - :investigation-timeout-ms (default 600000)
   - :evaluation-timeout-ms (default 600000)
   - :verbose (default false) - print progress"
  [prompt & {:keys [investigation-timeout-ms evaluation-timeout-ms verbose]
             :or {investigation-timeout-ms 600000
                  evaluation-timeout-ms 600000
                  verbose false}}]
  (when verbose
    (println "Starting parallel investigations...")
    (flush))

  (let [investigation-results (inv/run-investigators-parallel
                                prompt
                                :timeout-ms investigation-timeout-ms)]
    (when verbose
      (println "Investigations complete. Running evaluator...")
      (flush))

    (let [evaluation-result (invoke-evaluator
                              prompt
                              investigation-results
                              :timeout-ms evaluation-timeout-ms)]
      {:original-prompt prompt
       :investigations investigation-results
       :evaluation evaluation-result})))
