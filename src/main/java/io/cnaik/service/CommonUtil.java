package io.cnaik.service;

import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.cnaik.GoogleChatNotification;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

import java.util.logging.Level;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;

public class CommonUtil {

    private GoogleChatNotification googleChatNotification;
    private TaskListener taskListener;
    private FilePath ws;

    private final static Logger LOGGER = Logger.getLogger(CommonUtil.class.getName());

    public CommonUtil(GoogleChatNotification googleChatNotification,
                      TaskListener taskListener,
                      FilePath ws) {
        this.googleChatNotification = googleChatNotification;
        this.taskListener = taskListener;
        this.ws = ws;
    }

    public boolean sendNotification(String json) {

        String[] urlDetails = googleChatNotification.getUrl().split(",");
        Response response = null;

        for(String urlDetail: urlDetails) {

            String[] url = urlDetail.split("\\?");

            response = given(with().baseUri(url[0])).contentType(ContentType.JSON).queryParam(url[1]).urlEncodingEnabled(false).body(json).log()
                    .all().post();
        }

        LOGGER.log(Level.INFO, "Chat Notification Response: " + response.print());

        if(taskListener != null) {
            taskListener.getLogger().println("Chat Notification Response: " + response.print());
        }

        return response.statusCode() == HttpStatus.SC_OK ? true : false;
    }

    public String formResultJSON(Run build) {

        String defaultMessage = escapeSpecialCharacter(replaceJenkinsKeywords(googleChatNotification.getMessage(), build));
        return "{ 'text': '" + defaultMessage + "'}";
    }

    public String replaceJenkinsKeywords(String inputString, Run build) {

        if(StringUtils.isEmpty(inputString)) {
            return inputString;
        }

        try {

            if(taskListener != null) {
                taskListener.getLogger().println("ws: " + ws + " , build: " + build);
            }

            return TokenMacro.expandAll(build, ws, taskListener, inputString, false, null);
        } catch (Exception e) {
            if(taskListener != null) {
                taskListener.getLogger().println("Exception in Token Macro expansion: " + e);
            }
        }
        return inputString;
    }

    public boolean checkWhetherToSend(Run build) {

        boolean result = false;

        if(build == null || build.getResult() == null || googleChatNotification == null) {
            return result;
        }

        Run prevRun = build.getPreviousBuild();
        Result previousResult = (prevRun != null) ? prevRun.getResult() : Result.SUCCESS;

        if(googleChatNotification.isNotifyAborted()
                && Result.ABORTED == build.getResult()) {

            result = true;

        } else if(googleChatNotification.isNotifyFailure()
                && Result.FAILURE == build.getResult()) {

            result = true;

        } else if(googleChatNotification.isNotifyNotBuilt()
                && Result.NOT_BUILT == build.getResult()) {

            result = true;

        } else if(googleChatNotification.isNotifySuccess()
                && Result.SUCCESS == build.getResult()) {

            result = true;

        } else if(googleChatNotification.isNotifyUnstable()
                && Result.UNSTABLE == build.getResult()) {

            result = true;

        } else if(googleChatNotification.isNotifyBackToNormal() && Result.SUCCESS == build.getResult()
                    && (   Result.ABORTED == previousResult
                        || Result.FAILURE == previousResult
                        || Result.UNSTABLE == previousResult
                        || Result.NOT_BUILT == previousResult) ) {

            result = true;

        }

        return result;
    }

    public boolean checkPipelineFlag(Run build) {
        if(googleChatNotification != null &&
                !googleChatNotification.isNotifyAborted() &&
                !googleChatNotification.isNotifyBackToNormal() &&
                !googleChatNotification.isNotifyFailure() &&
                !googleChatNotification.isNotifyNotBuilt() &&
                !googleChatNotification.isNotifySuccess() &&
                !googleChatNotification.isNotifyUnstable()) {
            return true;
        }
        return checkWhetherToSend(build);
    }

    public String escapeSpecialCharacter(String input) {

        String output = input;

        if(StringUtils.isNotEmpty(output)) {
            output = output.replace("{", "\\{");
            output = output.replace("}", "\\}");
            output = output.replace("'", "\\'");
        }

        return output;
    }
}
