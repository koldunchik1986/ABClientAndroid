//git clone https://github.com/anti-captcha/anticaptcha-java.git

package com.anti_captcha;

import com.anti_captcha.Api.ImageToText;
import com.anti_captcha.Helper.DebugHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

public class Main {

    public static void main(String[] args) throws InterruptedException, MalformedURLException, JSONException {
        DebugHelper.setVerboseMode(true);

        ImageToText api = new ImageToText();
        api.setClientKey("YOUR_API_KEY_HERE");
        api.setFilePath("captcha.jpg");

        //Specify softId to earn 10% commission with your app.
        //Get your softId here: https://anti-captcha.com/clients/tools/devcenter
        api.setSoftId(0);

        //optional parameters, see documentation
        //api.setPhrase(true);                                    // 2 words
        //api.setCase(true);                                      // case sensitivity
        //api.setNumeric(ImageToText.NumericOption.NUMBERS_ONLY); // only numbers
        //api.setMath(1);                                         // do math operation like result of 50+5
        //api.setMinLength(1);                                    // minimum length of the captcha text
        //api.setMaxLength(10);                                   // maximum length of the captcha text
        //api.setLanguagePool("en");                              // set pool of the languages used in the captcha

        if (!api.createTask()) {
            DebugHelper.out(
                    "API v2 send failed. " + api.getErrorMessage(),
                    DebugHelper.Type.ERROR
            );
        } else if (!api.waitForResult()) {
            DebugHelper.out("Could not solve the captcha.", DebugHelper.Type.ERROR);
        } else {
            DebugHelper.out("Result: " + api.getTaskSolution().getText(), DebugHelper.Type.SUCCESS);
        }
    }

}