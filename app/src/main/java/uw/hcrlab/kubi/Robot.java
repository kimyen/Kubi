package uw.hcrlab.kubi;

import android.app.Activity;
import android.content.Context;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.firebase.client.AuthData;
import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.revolverobotics.kubiapi.IKubiManagerDelegate;
import com.revolverobotics.kubiapi.Kubi;
import com.revolverobotics.kubiapi.KubiManager;
import com.revolverobotics.kubiapi.KubiSearchResult;

import java.util.ArrayList;
import java.util.List;

import sandra.libs.asr.asrlib.ASR;
import sandra.libs.tts.TTS;
import sandra.libs.vpa.vpalib.Bot;
import uw.hcrlab.kubi.screen.RobotFace;
import uw.hcrlab.kubi.speech.SpeechUtils;
import uw.hcrlab.kubi.wizard.Response;

/**
 * Created by kimyen on 4/7/15.
 */
public class Robot extends ASR implements IKubiManagerDelegate {
    public static String TAG = Robot.class.getSimpleName();

    private static Robot robotInstance = null;

    private RobotThread thread;
    private RobotFace robotFace;
    private KubiManager kubiManager;

    // The ID of the bot to use for the chatbot, can be changed
    // you can also make a new bot by creating an account in pandorabots.com and making a new chatbot robot
    private String PANDORA_BOT_ID = "b9581e5f6e343f72";
    private Bot bot;
    private TTS tts;

    private Context currentCxt;

    private Firebase fb = new Firebase("https://hcrkubi.firebaseio.com");
    private Firebase responses;

    /**
     *  This class implements the Singleton pattern. Note that only the tts engine and RobotFace
     *  are updated when getInstance() is called.
     */
    private Robot(RobotFace face, final Context context){
        //Only one copy of this ever
        kubiManager = new KubiManager(this, true);
        kubiManager.findAllKubis();

        createRecognizer(App.getContext());

        setup(face, context);

        fb.authWithPassword("hcr@cs.uw.edu", "hcrpass", new Firebase.AuthResultHandler() {
            @Override
            public void onAuthenticated(AuthData authData) {
                Toast.makeText(currentCxt, "Logged In", Toast.LENGTH_SHORT);
                Log.d(TAG, "Authenticated");
            }

            @Override
            public void onAuthenticationError(FirebaseError firebaseError) {
                Toast.makeText(currentCxt, "Auth Error", Toast.LENGTH_SHORT);
                Log.e(TAG, "Authentication Error!");
                Log.e(TAG, firebaseError.toString());
            }
        });

        responses = fb.child("response");

        responses.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot ref, String s) {
                Log.d(TAG, "Child Added!");

                if(!ref.child("handled").getValue(Boolean.class)) {

                    Log.d(TAG, "Child Added - unhandled!");
                }
                //String res = dataSnapshot.getValue(String.class);
                //Toast.makeText(currentCxt, "Message: " + res, Toast.LENGTH_SHORT);
                //Log.e(TAG, res.getSpeech().getText());
                //say(res.getSpeech().getText());
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
    }

    public void cleanup() {
        kubiManager.disconnect();
    }

    /**
     * Gets the singleton instance of the Robot object. Note that after calling this method, the
     * robot.startup() method must be called, or the RobotFace will never be drawn.
     *
     * @param face A RobotFace view that can be drawn to
     * @param context The current activity
     * @return The Robot singleton
     */
    public static Robot getInstance(RobotFace face, Context context) {
        if (robotInstance == null) {
            //Create the singleton instance
            robotInstance = new Robot(face, context);

        } else {
            //Shutdown resources tied to the previous robot face to allow them to be recreated
            robotInstance.shutdown();
            robotInstance.tts.shutdown();

            //Setup the Robot instance with the new face
            robotInstance.setup(face, context);
        }

        return robotInstance;
    }

    /**
     * Handles the setup actions which must occur every time a new face is passed in.
     *
     * @param face The RobotFace view for the current Activity
     * @param context The current activity
     */
    private void setup(RobotFace face, Context context) {
        currentCxt = context;

        robotFace = face;
        robotFace.setOnTouchListener(faceListener);

        tts = TTS.getInstance(context);
        bot = new Bot((Activity)context, PANDORA_BOT_ID, tts);

        thread = new RobotThread(robotFace, kubiManager);
    }

    /**
     * Starts the robot by starting the RobotThread if it has not already been started.
     */
    public void startup() {
        if (thread.isAlive()) {
            Log.i(TAG, "Robot already started ...");
            return;
        }

        thread.start();
    }

    /**
     * Stops the RobotThread
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down Main Thread ...");

        while (true) {
            try {
                thread.setRunning(false);
                thread.join();
                return;
            } catch (InterruptedException e) {
                Log.e(TAG, "Robot thread didn't join. Trying again.");
            }
        }
    }

    /**
     * Generates text-to-Speech for the provided message.
     *
     * @param msg Message to speak
     */
    public void say(String msg) {
        try {
            tts.speak(msg, "EN");
        } catch (Exception e) {
            Log.e(TAG, "English not available for TTS, default language used instead");
        }
    }

    public void listen() {
        Log.i(TAG, "listening");
        try {
            super.listen(RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH, 1);
        } catch (Exception ex) {
            Toast.makeText(currentCxt, "ASR could not be started: invalid params", Toast.LENGTH_SHORT).show();
            Log.e(TAG, ex.getMessage());
        }
    }

    /**
     * Touch listener for the RobotFace
     */
    private View.OnTouchListener faceListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            Log.i(TAG, "RobotFace touch occured!");
            return false;
        }
    };

    /* IKubiManagerDelegate methods */

    @Override
    public void kubiDeviceFound(KubiManager manager, KubiSearchResult result) {
        Log.i(TAG, "A kubi device was found");
        // Attempt to connect to the kubi
        manager.connectToKubi(result);
    }

    @Override
    public void kubiManagerFailed(KubiManager manager, int reason) {
        Log.i(TAG, "Failed. Reason: " + reason);
        if (reason == KubiManager.FAIL_CONNECTION_LOST || reason == KubiManager.FAIL_DISTANCE) {
            manager.findAllKubis();
        }
    }

    @Override
    public void kubiManagerStatusChanged(KubiManager manager, int oldStatus, int newStatus) {
        // When the Kubi has successfully connected, nod as a sign of success
        if (newStatus == KubiManager.STATUS_CONNECTED && oldStatus == KubiManager.STATUS_CONNECTING) {
            Kubi kubi = manager.getKubi();
            kubi.performGesture(Kubi.GESTURE_NOD);
        }
    }

    @Override
    public void kubiScanComplete(KubiManager manager, ArrayList<KubiSearchResult> result) {
        Log.i(TAG, "Kubi scan completed");
        Log.i(TAG, "Size of result is " + result.size());
        if(result.size() > 0) {
            manager.stopFinding();
            // Attempt to connect to the kubi
            manager.connectToKubi(result.get(0));
        }
    }

    @Override
    public void processAsrResults(ArrayList<String> nBestList, float[] nBestConfidences) {
        String speechInput = nBestList.get(0);
        Log.i(TAG, "Speech input: " + speechInput);

        String response = SpeechUtils.getResponse(speechInput);

        try {
            if(response != null){
                //We have a preprogrammed Response, so use it
                Log.i(TAG, "Saying : " + response);
                say(response);
            }  else {
                //We don't have a preprogrammed Response, so use the bot to create a Response
                Log.i(TAG, "Default response");
                bot.initiateQuery(speechInput);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error responding to speech input: " + speechInput);
            e.printStackTrace();
        }
    }

    @Override
    public void processAsrReadyForSpeech() {
        Log.i(TAG, "Listening to user's speech.");
        Toast.makeText(currentCxt, "I'm listening.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void processAsrError(int errorCode) {
        String errorMessage = SpeechUtils.getErrorMessage(errorCode);

        if (errorMessage != null) {
            say(errorMessage);
        }

        // If there is an error, shows feedback to the user and writes it in the log
        Log.e(TAG, "Error: "+ errorMessage);
        Toast.makeText(currentCxt, errorMessage, Toast.LENGTH_LONG).show();
    }
}
