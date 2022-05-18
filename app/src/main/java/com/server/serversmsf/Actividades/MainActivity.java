package com.server.serversmsf.Actividades;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import com.server.serversmsf.modelo.OTPs;
import com.server.serversmsf.modelo.GuardarEnDB;
import com.server.serversmsf.modelo.ListCode;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.server.serversmsf.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ServerF";
    private FirebaseFirestore mDatabase;
    private String nTel, msg, telVerif, telAux, hashkey;
    private int rCode, codeTel;
    private TextView textView, textAbajo;
    private FirebaseApp app;
    private FirebaseAuth auten;
    private String auth;
    private OTPs clave;
    private Button borrar;
    private GuardarEnDB gdb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Esconder la barra superior de la APP
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.hide();

        //Enlazar views
        textView = (TextView) findViewById(R.id.text);
        textAbajo = (TextView) findViewById(R.id.textabajo);
        borrar = (Button) findViewById(R.id.buttonBorrar);
        //Instanciar la base de datos
        mDatabase = FirebaseFirestore.getInstance();
        //Autenticar en la BD

        app = FirebaseApp.initializeApp(this);
        auten = FirebaseAuth.getInstance();

        //Instanciamos el modelo
        clave = new OTPs();
        gdb = new GuardarEnDB();

        //Pedir permisos de la aplicación
        int permissionCheck = ContextCompat.checkSelfPermission(
                this, Manifest.permission.SEND_SMS);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.d("Permisos", "Pedir permisos");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, 225);
        } else {
            Log.d("Permisos", "Se otorgaron los permisos");
        }

        //Obtener token de Auth
        String url = "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword?key=AIzaSyCO0wQa_fia6ojLkFCzLG-sft5XUWF2Skw";
        // Request a string response from the provided URL.
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        JSONObject postData = new JSONObject();
        try {
            postData.put("email", "a@a.com");
            postData.put("password", "123456");
            postData.put("returnSecureToken", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JsonObjectRequest jsonObjectPost = new JsonObjectRequest(Request.Method.POST, url, postData, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    auth = response.getString("idToken");
                    //textView.setText(response.getString("idToken"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        });
        requestQueue.add(jsonObjectPost);
        //Obtener tel ---->
        auten.signInWithEmailAndPassword("a@a.com", "123456").addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                DatabaseReference myRef = FirebaseDatabase.getInstance().getReference();
                myRef.child("numeros").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
                        String url2 = "https://smsretrieverservera-default-rtdb.europe-west1.firebasedatabase.app/numeros.json?auth="+auth;
                        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                                (Request.Method.GET, url2, null, new Response.Listener<JSONObject>() {
                                    @Override
                                    public void onResponse(JSONObject response) {
                                        try {
                                            Log.d(TAG, "Response length: "+response.length());
                                            if(response.has("otp")){
                                                Log.d(TAG, "OTP: "+response.getInt("otp"));
                                            }
                                            if (response.length() == 2 && nTel==null && rCode==0) {
                                                Toast.makeText(getApplicationContext(), "Se manda tel y hash", Toast.LENGTH_SHORT).show();
                                                nTel = response.getString("tel");
                                                hashkey = response.getString("hash");
                                                textView.setText("Recibida petición de: " + nTel);
                                                clave.setNumtel(nTel);  //Añadimos el teléfono al Modelo
                                            } else if ((response.length() == 2) && (response.has("otp"))) {
                                                telVerif = response.getString("tel");
                                                codeTel = response.getInt("otp");
                                                Toast.makeText(getApplicationContext(), "Se verifica el OTP", Toast.LENGTH_SHORT).show();
                                                textView.setText("Recibida petición de verificación de: " + telVerif);
                                                textAbajo.setText("El código OTP recibido es: " + codeTel);
                                                clave.setNumtel(String.valueOf(codeTel));  //Añadimos el OTP code al Modelo
                                                if (telVerif != null && codeTel != 0 && (telVerif.equals(telAux)) && (codeTel==rCode)) {
                                                    String token = generarToken(12); //Generamos el token
                                                    //Vaciamos los valores internos para siguientes llamadas

                                                    RequestQueue requestTokenQueue = Volley.newRequestQueue(MainActivity.this);
                                                    JSONObject tokenData = new JSONObject();
                                                    String url ="https://smsretrieverservera-default-rtdb.europe-west1.firebasedatabase.app/numeros.json?auth="+auth;
                                                    try {
                                                        tokenData.put("token", token);
                                                        textAbajo.setTextColor(Color.parseColor("#03A9F4"));
                                                        textAbajo.setText("El token de la conexión con el cliente es " +token);
                                                    } catch (JSONException e) {
                                                        e.printStackTrace();
                                                    }
                                                    // Borramos la info en la URL.
                                                    JsonObjectRequest jsonDelObjectRequest = new JsonObjectRequest(Request.Method.PUT, url, tokenData, new Response.Listener<JSONObject>() {
                                                        @Override
                                                        public void onResponse(JSONObject response) {
                                                            Log.d(TAG, "¡Canal de comunicación solo con token!");
                                                        }
                                                    }, new Response.ErrorListener() {
                                                        @Override
                                                        public void onErrorResponse(VolleyError error) {
                                                            error.printStackTrace();
                                                        }
                                                    });
                                                    requestTokenQueue.add(jsonDelObjectRequest);
                                                    gdb.guardarDatos(clave, "verifCodes");  //Guardamos el dato al haberse verificado en la BD
                                                    telVerif = null;
                                                    rCode=0;
                                                }

                                            } else if (response.length() == 0){
                                                textView.setText("Canal de comunicación borrado");
                                            }else if (response.length() == 1 && !(response.has("token"))) {     //Si solo se manda el num de telefono
                                                //Borramos los datos que se han enviado
                                                String respAnt=response.getString("tel"); //Telefono anterior
                                                RequestQueue requestTokenQueue = Volley.newRequestQueue(MainActivity.this);
                                                JSONObject tokenData = new JSONObject();
                                                String url ="https://smsretrieverservera-default-rtdb.europe-west1.firebasedatabase.app/numeros.json?auth="+auth;
                                                // Borramos la info en la URL.
                                                StringRequest deleteRequest = new StringRequest(Request.Method.DELETE, url, new Response.Listener<String>() {
                                                    @Override
                                                    public void onResponse(String response) {
                                                        textAbajo.setTextColor(Color.parseColor("#03A9F4"));
                                                        textView.setText("ERROR se ha recibido solo el telefono: "+respAnt);
                                                        textAbajo.setText("Borrando los datos del canal de comunicación");
                                                    }
                                                }, new Response.ErrorListener() {
                                                    @Override
                                                    public void onErrorResponse(VolleyError error) {
                                                        error.printStackTrace();
                                                    }
                                                });
                                                requestTokenQueue.add(deleteRequest);
                                            }
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }//FIN IF de posibles casos ondatachange
                                        if (nTel != null && hashkey!=null) {
                                            //Creamos el nuevo codigo OTP.
                                            rCode = crearCodigo();
                                            textAbajo.setTextColor(Color.parseColor("#D51818"));
                                            textAbajo.setText("El código OTP enviado es: " + rCode);
                                            msg = crearMensaje(hashkey);
                                            sendSMS(nTel, msg);
                                            //CADUCIDAD
                                            Date tiempoAct= Timestamp.now().toDate();
                                            long miliDate= tiempoAct.getTime();
                                            clave.setCaducidad(new Date(miliDate+(5 * 60 * 1000)).toString());
                                            clave.setCode(rCode);   //Metemos la clave OTP en el Modelo
                                            Log.d(TAG, "Se crea el SMS y se envía" );
                                            telAux = nTel;
                                            Log.d(TAG, "Se borra hashkey" );
                                            hashkey=null;
                                            //Si no hay num tel no escribe en la BD
                                            Toast.makeText(getApplicationContext(), "El num de tel es: " + nTel, Toast.LENGTH_SHORT).show();
                                            //Guardamos el codigo en la BD
                                            gdb.guardarDatos(clave, "listcode");            //Llamamos al modelo para guardar los datos
                                            nTel=null;  //Vaciamos el valor.
                                        }//Fin IF
                                    }
                                }, new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError error) {
                                        Toast.makeText(getApplicationContext(), "No hay mensajes", Toast.LENGTH_LONG).show();
                                    }
                                });
                        queue.add(jsonObjectRequest);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.w("SE BORRAN DATOS", "Se están borrando datos");
                    }
                });
            }
        });
    }
    //Obtenemos los datos que queremos guardar en la BD para tener un control

    //Método que crea el código OTP aleatorio.
    private int crearCodigo() {
        int num;
        num = new Random().nextInt(900000) + 100000;
        return num;
    }

    //Método para generar un token aleatorio de una longitud dad por parametro para la conexión
    private String generarToken(int lon){
        String posibleChar = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";          //Posibles chars
        String token = null;
        StringBuilder tokenB = new StringBuilder(lon);              //Generamos una cadena de tam max = lon
        for (int i = 0; i < lon; i++) {
            tokenB.append(posibleChar.charAt(new Random().nextInt(posibleChar.length())));   //Añadimos un nuevo char a la cadena
        }
        token =tokenB.toString();
        return token;
    }

    private String crearMensaje(String h) {
        String msg;
        msg = "Tú código OTP es: " + rCode + "\n" + h;
        return msg;
    }

    //https://localcoder.org/how-to-monitor-each-of-sent-sms-status
    private void sendSMS(String numeroTel, String mensaje) {
        String enviado = "SMS_SENT";
        String recibido = "SMS_DELIVERED";

        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(enviado), FLAG_IMMUTABLE);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, new Intent(recibido), FLAG_IMMUTABLE);

        //---when the SMS has been sent---
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        Toast.makeText(getBaseContext(), "SMS enviado", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Toast.makeText(getBaseContext(), "Fallo genérico", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Toast.makeText(getBaseContext(), "Sin servicio error", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Toast.makeText(getBaseContext(), "Null PDU", Toast.LENGTH_SHORT).show();
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Toast.makeText(getBaseContext(), "Radio apagada", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }, new IntentFilter(enviado));

        //---when the SMS has been delivered---
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        Toast.makeText(getBaseContext(), "SMS mandado correctamente", Toast.LENGTH_SHORT).show();
                        break;
                    case Activity.RESULT_CANCELED:
                        Toast.makeText(getBaseContext(), "Ha fallado el envío de SMS", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }, new IntentFilter(recibido));

        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(numeroTel, null, mensaje, sentPI, deliveredPI);
    }

    public void onBorrar(View view) {
        //Borramos los datos que se han enviado
        RequestQueue requestTokenQueue = Volley.newRequestQueue(MainActivity.this);
        JSONObject tokenData = new JSONObject();
        String url ="https://smsretrieverservera-default-rtdb.europe-west1.firebasedatabase.app/numeros.json?auth="+auth;
        // Borramos la info en la URL.
        StringRequest deleteRequest = new StringRequest(Request.Method.DELETE, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                textAbajo.setTextColor(Color.parseColor("#009688"));
                textView.setText("Borrando datos obsoletos");
                textAbajo.setText("Se han borrado los datos sastifactoriamene");
                nTel=null;
                telVerif=null;
                telAux=null;
                codeTel=0;
                rCode=0;
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        });
        requestTokenQueue.add(deleteRequest);
    }
}