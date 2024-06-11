package maestro.cli.report

import maestro.cli.model.TestExecutionSummary
import maestro.cli.report.TestDebugReporter
import okio.Sink
import okio.buffer
import java.io.BufferedReader
import java.io.FileReader
import kotlinx.html.*
import kotlinx.html.stream.appendHTML

class HtmlTestSuiteReporter : TestSuiteReporter {
  override fun report(summary: TestExecutionSummary, out: Sink) {
    val bufferedOut = out.buffer()
    val htmlContent = buildHtmlReport(summary)
    bufferedOut.writeUtf8(htmlContent)
    bufferedOut.close()
  }

  private fun getTestStep(): Array<Array<String>> {
        val debugOutputPath = TestDebugReporter.getDebugOutputPath()
        val filePathLog = "${debugOutputPath}/maestro.log"
        var reader: BufferedReader? = null
        var testStep = emptyArray<String>()
        var testSteps = emptyArray<Array<String>>()
    
        try {
            reader = BufferedReader(FileReader(filePathLog))
            var line: String = ""
            var nextLine: String = ""
    
            while (reader.readLine().also { line = it } != null) {
                // Process each line
                if(line.contains("m.cli.runner.TestSuiteInteractor") && !line.contains("Run") && !line.contains("Define variables") && !line.contains("Apply configuration") && !line.contains("APP_ID")){ 
                  if(line.contains("RUNNING") && line.contains("Input")) {
                    nextLine = reader.readLine()
                    testStep += nextLine.replace("[INFO ] maestro.Maestro ", "")
                  }
                  if(line.contains("COMPLETED") || line.contains("FAILED")){
                      testStep += line.replace("[INFO ] m.cli.runner.TestSuiteInteractor ", "")
                  }
                }
                if(line.contains("Launching app") || line.contains("Stopping app")) {
                  testStep += line.replace("[INFO ] maestro.Maestro ", "")
                }
                if(line.contains("m.cli.runner.TestSuiteInteractor - Stop") && line.contains("COMPLETED")){
                    testSteps += testStep
                    testStep = emptyArray<String>()
                }
            }
        } catch (e: Exception) {
        } finally {
            try {
                reader?.close()
            } catch (e: Exception) {
                println("An error occurred while closing the file: ${e.message}")
            }
        }
        return testSteps
  }

  private fun getFailedTest(summary: TestExecutionSummary): Array<String>{
    var failedTest = emptyArray<String>()
    for (suite in summary.suites) {
        for(flow in suite.flows){
            if(flow.status.toString() == "ERROR"){
                failedTest += flow.name
            }
        }
    }
    return failedTest
  }

  private fun buildHtmlReport(summary: TestExecutionSummary): String {
    var failedTest = getFailedTest(summary)
    val testSteps = getTestStep()
    var idx = 0
    return buildString {
      appendHTML().html {
        head {
          title { +"Maestro Test Report" }
          link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css") {}
        }
        body {
          summary.suites.forEach { suite ->
            div(classes = "card mb-4") {
              div(classes = "card-body") {
                h1(classes = "mt-5 text-center") { +"Flow Execution Summary" }
                br{}
                +"Test Result: ${if (suite.passed) "PASSED" else "FAILED"}"
                br{}
                +"Duration: ${suite.duration}"
                br{}
                br{}
                div(classes = "card-group mb-4") {
                  div(classes = "card") {
                    div(classes = "card-body") {
                      h5(classes = "card-title text-center") { +"Total number of Flows" }
                      h3(classes = "card-text text-center") { +"${suite.flows.size}" }
                    }
                  }
                  div(classes = "card text-white bg-danger") {
                    div(classes = "card-body") {
                      h5(classes = "card-title text-center") { +"Failed Flows" }
                      h3(classes = "card-text text-center") { +"${failedTest.size}" }
                    }
                  }
                  div(classes = "card text-white bg-success") {
                    div(classes = "card-body") {
                      h5(classes = "card-title text-center") { +"Successful Flows" }
                      h3(classes = "card-text text-center") { +"${suite.flows.size - failedTest.size}" }
                    }
                  }
                }
                if(failedTest.size != 0){
                  div(classes = "card border-danger mb-3") {
                    div(classes = "card-body text-danger") {
                      b { +"Failed Flow" }
                      br{}
                      p(classes = "card-text") {
                        failedTest.forEach { test ->
                          +"${test}"
                          br{}
                        }
                      }
                    }
                  }
                }
                suite.flows.forEach { flow ->
                  val buttonClass = if (flow.status.toString() == "ERROR") "btn btn-danger" else "btn btn-success"
                  div(classes = "card mb-4") {
                    div(classes = "card-header") {
                      h5(classes = "mb-0") {
                        button(classes = "$buttonClass") {
                          attributes["type"] = "button"
                          attributes["data-bs-toggle"] = "collapse"
                          attributes["data-bs-target"] = "#${flow.name}"
                          attributes["aria-expanded"] = "false"
                          attributes["aria-controls"] = "${flow.name}"
                          +"${flow.name} : ${flow.status}"
                        }
                      }
                    }
                    div(classes = "collapse") {
                      id = "${flow.name}"
                      div(classes = "card-body") {
                        div(classes = "row") {
                          div(classes = "col-md-8") {
                            p(classes = "card-text") {
                              +"Status: ${flow.status}"
                              br{}
                              +"Duration: ${flow.duration}"
                              br{}
                              +"File Name: ${flow.fileName}"
                            }
                            if(flow.failure != null) {
                              p(classes = "card-text text-danger"){
                                +"${flow.failure.message}"
                              }
                            }
                            div(classes = "accordion") {
                              div(classes = "accordion-item") {
                                h5(classes = "accordion-header") {
                                  button(classes = "accordion-button border-danger") {
                                    attributes["types"] = "button"
                                    attributes["data-bs-toggle"] = "collapse"
                                    attributes["data-bs-target"] = "#step-${flow.name}"
                                    attributes["aria-expanded"] = "false"
                                    attributes["aria-controls"] = "step-${flow.name}"
                                    +"Test Step Details"
                                  }
                                }
                                div(classes = "collapse") {
                                  id = "step-${flow.name}"
                                  div(classes = "accordion-body") {
                                    attributes["style"] = "max-height: 200px; overflow-y: auto;"
                                    for (step in testSteps[idx]) {
                                      +"${step}"
                                      br{}
                                    }
                                  }
                                  idx++
                                }
                              }
                            }
                          }
                          div(classes = "col-md-4") {
                            div(classes = "text-center") {
                              if(flow.failure != null) {
                                img(classes = "img-fluid") {
                                  attributes["src"] = "screenshot-❌-(${flow.name}).png"
                                  attributes["width"] = "50%"
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
              script(src = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.min.js", content = "")
            }
          }
        }
      }
    }
  }
}