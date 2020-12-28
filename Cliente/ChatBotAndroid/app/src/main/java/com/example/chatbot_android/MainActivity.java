package com.example.chatbot_android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int USER = 10001;
    private static final int BOT = 10002;

    private EditText editText;
    private LinearLayout chatLayout;

    // For initChatbot
    private SessionsClient sessionsClient;
    private SessionName session;

    // Crea un id para el cliente
    private String uuid = UUID.randomUUID().toString();

    //
    private String voiceInput;
    private String message;

    // IDs para las app a abrir
    private static final String DEFAULT_RESP = "Esa app no está instalada";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("UsrID", uuid);
        chatLayout = findViewById(R.id.chatLayout);

        //Connect to Dialogflow API
        initChatbot();

        ImageView sendButton = findViewById(R.id.sendBtn);
        ImageView microphoneButton = findViewById(R.id.microphoneBtn);
        editText = findViewById(R.id.queryEditText);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                message = editText.getText().toString();
                if(!message.equals("")) {
                    sendMessage(v);
                }

            }
        });

        microphoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSpeechInput(v);
            }
        });

    }

    // INITIALIZE CLIENT API

    private void initChatbot(){
        try {
            InputStream stream = getResources().openRawResource(R.raw.agent_credentials);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream);
            String projectId = ((ServiceAccountCredentials) credentials).getProjectId();

            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
            sessionsClient = SessionsClient.create(sessionsSettings);
            session = SessionName.of(projectId, uuid);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    // SEND A TEXT MSG
    public void sendMessage(View view) {

        showTextView(message, USER);
        editText.setText("");

        QueryInput queryInput = QueryInput.newBuilder().setText(TextInput.newBuilder().setText(message).setLanguageCode("es")).build();
        Log.d("QUERY", queryInput.toString());
        new RequestJavaV2Task(MainActivity.this, session, sessionsClient, queryInput).execute();


    }

    public void callback(DetectIntentResponse response) {

        if (response != null) {
            String botReply = response.getQueryResult().getFulfillmentText();
            String intent = response.getQueryResult().getIntent().getDisplayName();

            if(intent.equals("openApp")){

                if(AppIsInstalled(botReply, getApplicationContext())) {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(botReply);
                    startActivity(launchIntent);
                    showTextView("Abriendo...", BOT);

                } else{
                    showTextView("Esa app no está disponible" , BOT);
                }

            }else{
                showTextView(botReply , BOT);
            }



        } else {
            Log.d(TAG, "Bot Reply: Null");
        }
    }

    //Comprueba si la app está instalada en el dispositivo
    private boolean AppIsInstalled(String nombrePaquete, Context context) {

        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(nombrePaquete, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    // ADD VIEW TO MESSAGE

    private void showTextView(String message, int type) {
        FrameLayout layout;
        switch (type) {
            case USER:
                layout = getUserLayout();
                break;
            case BOT:
                layout = getBotLayout();
                break;
            default:
                layout = getBotLayout();
                break;
        }
        layout.setFocusableInTouchMode(true);
        chatLayout.addView(layout);
        TextView tv = layout.findViewById(R.id.chatMsg);
        tv.setText(message);
        layout.requestFocus();
        editText.requestFocus();
    }


    // SPEECH TO TEXT METHODS

    public void getSpeechInput(View view){

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        if (intent.resolveActivity(getPackageManager()) != null){
            startActivityForResult(intent, 10);
        }else{
            Toast.makeText(this,"Su dispositivo no soporta la entrada de audio", Toast.LENGTH_SHORT).show();
        }

    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 10) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                voiceInput = result.get(0);
                editText.setText(voiceInput);
            }
        }
    }


    //  LAYOUT INFLATERS

    @SuppressLint("InflateParams")
    FrameLayout getUserLayout(){
        LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
        return (FrameLayout) inflater.inflate(R.layout.user_msg_layout, null);
    }

    @SuppressLint("InflateParams")
    FrameLayout getBotLayout(){
        LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
        return (FrameLayout) inflater.inflate(R.layout.bot_msg_layout, null);
    }
}
