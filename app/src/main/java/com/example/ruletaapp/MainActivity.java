package com.example.ruletaapp;
import static androidx.core.content.ContextCompat.getSystemService;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
//import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.RelativeLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
//importem notificacions
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
//aquest import permet fer automaticament fora a l'usuari cap al menu
import android.os.Handler;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;


public class MainActivity extends AppCompatActivity {
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private static final int RC_SIGN_IN = 9001;
    private RouletteView rouletteView;
    private float currentAngle = 0f;
    private final Random random = new Random();
    private SoundManager soundManager;
    private ImageView monedaImage;
    private int monedes = 5;
    private TextView monedesText;
    private boolean canviIntern = false;
    private Button spinButton;
    private MediaPlayer mediaPlayer;
    private MediaPlayer mediaPlayerClick;
    private MediaPlayer mediaPlayerResultat; //reproductor per sons de resultat (guanyar o perdre)
    private static final int LIMIT_VICTORIA = 6000;
    private final String[] sectors = {
            "*1", "*2", "*2", "*2", "*3", "*1", "*3", "*3" //valors ruleta per guanyar rapid
            //"-1", "-2", "/2", "/2", "-3", "-1", "-3", "-3" //valors ruleta per perdre rapid
            //"+1", "-2", "*2", "/2", "+3", "-1", "*3", "-3"  // valors normals
    };
    private static final int REQUEST_LOCATION_PERMISSION = 123;
    private FusedLocationProviderClient fusedLocationClient;

    private double latitudActual = 0.0;
    private double longitudActual = 0.0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main); // això sempre abans dels findViewById
        soundManager = SoundManager.getInstance(this);
        soundManager.playBackgroundMusic(); // només després de setContentView()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);


        //log de google
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        mAuth = FirebaseAuth.getInstance();
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            String nom = account.getDisplayName();
            String email = account.getEmail();
            String id = account.getId();


            Log.d("LOGIN", "Usuari connectat: " + nom + " (" + email + ")");
        } else {
            Log.d("LOGIN", "Cap usuari loguejat");
        }

        // PERMÍS DE LOCALITZACIÓ
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            obtenirUbicacioActual(() -> {});
        }


        // PERMÍS PER NOTIFICACIONS (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        // PERMÍS PER ACCEDIR A IMATGES (API 33+) O FITXERS (API < 29)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 1001);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
            }
        }

        // A PARTIR D’AQUÍ continuem amb findViewById, listeners, etc.


        MonedaDao monedaDao = new MonedaDao(this);

        rouletteView = findViewById(R.id.rouletteView);
        spinButton = findViewById(R.id.spinButton);
        monedesText = findViewById(R.id.monedesText);
        monedaImage = findViewById(R.id.monedaImage);
        monedesText.setText("Monedes: " + monedes);

        LinearLayout menuLayout = findViewById(R.id.menuLayout);
        Button btnJugar = findViewById(R.id.btnJugar);
        Button btnSalir = findViewById(R.id.btnSalir);
        Button btnRetirar = findViewById(R.id.btnRetirar);
        Button btnPuntuaciones = findViewById(R.id.btnPuntuaciones);
        Button btnOpciones = findViewById(R.id.btnOpciones);
        Button btnGuardarCaptura = findViewById(R.id.btnGuardarCaptura);
        btnGuardarCaptura.setVisibility(View.GONE);
        btnGuardarCaptura.setOnClickListener(v -> guardarCapturaPantalla());

        btnRetirar.setVisibility(View.GONE);
        monedaImage.setVisibility(View.GONE);

        btnPuntuaciones.setOnClickListener(v -> {
            canviIntern = true;
            startActivity(new Intent(this, HistorialActivity.class));
        });

        btnJugar.setOnClickListener(v -> {
            RelativeLayout rootLayout = findViewById(R.id.rootLayout);
            rootLayout.setBackgroundResource(R.drawable.fons2);

            menuLayout.setVisibility(View.GONE);
            monedesText.setVisibility(View.VISIBLE);
            spinButton.setVisibility(View.VISIBLE);
            findViewById(R.id.ruletaContainer).setVisibility(View.VISIBLE);
            btnRetirar.setVisibility(View.VISIBLE);
            monedaImage.setVisibility(View.VISIBLE);
        });

        btnRetirar.setOnClickListener(v -> {
            FirebaseUser usuari = FirebaseAuth.getInstance().getCurrentUser();
            if (usuari != null) {
                String email = usuari.getEmail();
                long timestamp = System.currentTimeMillis();
                Puntuacio puntuacio = new Puntuacio(email, monedes, timestamp);

                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.collection("top_mundial")
                        .add(puntuacio)
                        .addOnSuccessListener(documentReference -> Log.d("FIRESTORE", "Puntuació pujada correctament"))
                        .addOnFailureListener(e -> Log.e("FIRESTORE", "Error pujant la puntuació", e));
            }
            obtenirUbicacioActual(() -> monedaDao.inserirPartida(monedes, latitudActual, longitudActual));
            monedaDao.inserirPartida(monedes, latitudActual, longitudActual);;
            menuLayout.setVisibility(View.VISIBLE);
            monedesText.setVisibility(View.GONE);
            spinButton.setVisibility(View.GONE);
            findViewById(R.id.ruletaContainer).setVisibility(View.GONE);
            btnRetirar.setVisibility(View.GONE);
            monedaImage.setVisibility(View.GONE);

            // tor el fons original
            RelativeLayout rootLayout = findViewById(R.id.rootLayout);
            rootLayout.setBackgroundResource(R.drawable.fons1);

            monedes = 5;
            monedesText.setText("Monedes: " + monedes);
            spinButton.setEnabled(true);
            spinButton.setAlpha(1f);
        });
        //boto login
        Button btnLogin = findViewById(R.id.btnLogin); // Assegura't que tens aquest botó al layout
        btnLogin.setOnClickListener(v -> signIn());
        //boto ajuda
        Button btnAjuda = findViewById(R.id.btnAjuda);
        btnAjuda.setOnClickListener(v -> {
            canviIntern = true;
            Intent intent = new Intent(MainActivity.this, HelpActivity.class);
            startActivity(intent);
        });

        btnOpciones.setOnClickListener(v -> {
            canviIntern = true;
            startActivity(new Intent(this, OpcionesActivity.class));
        });
        btnSalir.setOnClickListener(v -> finish());

        spinButton.setOnClickListener(v -> {
            int angle = 360 * (3 + random.nextInt(4)) + random.nextInt(360);
            float newAngle = currentAngle + angle;

            RotateAnimation rotate = new RotateAnimation(
                    currentAngle, newAngle,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
            );
            SharedPreferences prefsVfx = getSharedPreferences("audio_settings", MODE_PRIVATE);
            float volumVfx = prefsVfx.getInt("volum_vfx", 100) / 100f;

            soundManager.playClickSound();


            rotate.setDuration(3000);
            rotate.setFillAfter(true);

            rotate.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    float angleFinal = (360 - (newAngle % 360)) % 360f;
                    int sector = (int) (angleFinal / 45f);
                    String accio = sectors[sector];

                    monedes = aplicarAccio(monedes, accio);
                    monedesText.setText("Monedes: " + monedes);

                    //cridem el so segons el que surti, la funcio esta fora al main
                    SharedPreferences prefsVfx = getSharedPreferences("audio_settings", MODE_PRIVATE);
                    float volumVfx = prefsVfx.getInt("volum_vfx", 100) / 100f;

                    if (accio.contains("+") || accio.contains("*")) {
                        soundManager.playResultSound(R.raw.monedaguanyada1);
                    } else if (accio.contains("-") || accio.contains("/")) {
                        soundManager.playResultSound(R.raw.monedaperduda1);
                    }


                    //text del que ha surt de les monedes
                    Toast.makeText(MainActivity.this,
                            "Has tocat: " + accio + " → Monedes: " + monedes,
                            Toast.LENGTH_SHORT).show();

                    if (monedes >= LIMIT_VICTORIA) {
                        obtenirUbicacioActual(() -> monedaDao.inserirPartida(monedes, latitudActual, longitudActual));
                        mostrarNotificacio("Has guanyat!!!", "El teu rècord és de " + monedes + " monedes 🏆");
                        btnGuardarCaptura.setVisibility(View.VISIBLE);
                        monedaDao.inserirPartida(monedes, latitudActual, longitudActual);
                        spinButton.setEnabled(false);
                        spinButton.setAlpha(0.5f);

                        FirebaseUser usuari = FirebaseAuth.getInstance().getCurrentUser();
                        if (usuari != null) {
                            String email = usuari.getEmail();
                            long timestamp = System.currentTimeMillis();
                            Puntuacio puntuacio = new Puntuacio(email, monedes, timestamp);

                            FirebaseFirestore db = FirebaseFirestore.getInstance();
                            db.collection("top_mundial")
                                    .add(puntuacio)
                                    .addOnSuccessListener(documentReference -> Log.d("FIRESTORE", "Puntuació pujada correctament"))
                                    .addOnFailureListener(e -> Log.e("FIRESTORE", "Error pujant la puntuació", e));
                        }

                        // cridem el calendari DESPRÉS d’uns segons
                        new Handler().postDelayed(() -> {
                            // crida func afegir a calendari
                            afegirEsdevenimentCalendari();

                            Intent intent = new Intent(Intent.ACTION_INSERT);
                            intent.setData(CalendarContract.Events.CONTENT_URI);
                            intent.putExtra(CalendarContract.Events.TITLE, "Victòria a la Ruleta!");
                            intent.putExtra(CalendarContract.Events.DESCRIPTION, "Has guanyat amb " + monedes + " monedes 🏆");
                            intent.putExtra(CalendarContract.Events.EVENT_LOCATION, "RuletaApp");
                            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis());
                            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 60 * 60 * 1000); // 1 hora
                            startActivity(intent);
                            RelativeLayout rootLayout = findViewById(R.id.rootLayout);
                            rootLayout.setBackgroundResource(R.drawable.fons1);
                        }, 4000); // 4 segons de marge



                        // tornem al menú principal al cap de 6 segons
                        new Handler().postDelayed(() -> {
                            findViewById(R.id.menuLayout).setVisibility(View.VISIBLE);
                            monedesText.setVisibility(View.GONE);
                            spinButton.setVisibility(View.GONE);
                            findViewById(R.id.ruletaContainer).setVisibility(View.GONE);
                            findViewById(R.id.btnRetirar).setVisibility(View.GONE);
                            monedaImage.setVisibility(View.GONE);
                            findViewById(R.id.btnGuardarCaptura).setVisibility(View.GONE);
                            monedes = 5;
                            monedesText.setText("Monedes: " + monedes);
                            spinButton.setEnabled(true);
                            spinButton.setAlpha(1f);
                            RelativeLayout rootLayout = findViewById(R.id.rootLayout);
                            rootLayout.setBackgroundResource(R.drawable.fons1);
                        }, 6000);
                        // restaura el fons original
                    }

                    if (monedes <= 0) {
                        obtenirUbicacioActual(() -> monedaDao.inserirPartida(monedes, latitudActual, longitudActual));
                        monedaDao.inserirPartida(monedes, latitudActual, longitudActual);
                        spinButton.setEnabled(false);
                        spinButton.setAlpha(0.5f);

                        mostrarNotificacio("Game Over", "T'has quedat sense monedes 💀");

                        // tornem al menu 3 segons (per donar temps a veure el resultat)
                        new Handler().postDelayed(() -> {
                            findViewById(R.id.menuLayout).setVisibility(View.VISIBLE);
                            monedesText.setVisibility(View.GONE);
                            spinButton.setVisibility(View.GONE);
                            findViewById(R.id.ruletaContainer).setVisibility(View.GONE);
                            findViewById(R.id.btnRetirar).setVisibility(View.GONE);
                            monedaImage.setVisibility(View.GONE);

                            monedes = 5;
                            monedesText.setText("Monedes: " + monedes);
                            spinButton.setEnabled(true);
                            spinButton.setAlpha(1f);
                            RelativeLayout rootLayout = findViewById(R.id.rootLayout);
                            rootLayout.setBackgroundResource(R.drawable.fons1);
                        }, 3000);
                    }

                    soundManager.stopClickSound();
                }
                @Override public void onAnimationRepeat(Animation animation) {}
            });

            rouletteView.startAnimation(rotate);
            currentAngle = newAngle % 360;
        });
        String sortir = getString(R.string.btn_sortir);
        Log.d("IDIOMA_TEST", "Traducció actual de btn_sortir: " + sortir);
    }
    private void obtenirUbicacioActual(Runnable despresDeGuardar) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    latitudActual = location.getLatitude();
                    longitudActual = location.getLongitude();

                    Log.d("UBICACIO", "Lat: " + latitudActual + ", Lon: " + longitudActual);
                    new UbicacioDao(this).guardarUbicacio(latitudActual, longitudActual);
                } else {
                    Log.w("UBICACIO", "No s'ha pogut obtenir la ubicació");
                }
            });

        } else {
            Log.w("UBICACIO", "Permís no concedit");
            if (despresDeGuardar != null) {
                despresDeGuardar.run();
            }
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permís de localització concedit ✅", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permís de localització denegat ❌", Toast.LENGTH_SHORT).show();
            }
        }
    }


    //reproduccio del so
    private void playResultSound(int soundResId, float volume) {
        if (mediaPlayerResultat != null) {
            mediaPlayerResultat.release();
            mediaPlayerResultat = null;
        }

        try {
            AssetFileDescriptor afd = getResources().openRawResourceFd(soundResId);
            if (afd != null) {
                mediaPlayerResultat = new MediaPlayer();
                mediaPlayerResultat.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                mediaPlayerResultat.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayerResultat.setLooping(false);
                mediaPlayerResultat.prepare();
                mediaPlayerResultat.setVolume(volume, volume);
                mediaPlayerResultat.start();

                mediaPlayerResultat.setOnCompletionListener(mp -> {
                    mp.release();
                    mediaPlayerResultat = null;
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //notificacions
    private void mostrarNotificacio(String titol, String missatge) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String canalId = "canal_ruleta";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    canalId,
                    "Notificacions Ruleta",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(canal);
        }

        // Intent per tornar al menú (MainActivity)
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, canalId)
                .setSmallIcon(R.drawable.ic_launcher_foreground) //  es pot posar icona
                .setContentTitle(titol)
                .setContentText(missatge)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent); // Aquí!

        notificationManager.notify(new Random().nextInt(), builder.build());
    }


    private int aplicarAccio(int monedes, String accio) {
        try {
            if (accio.contains("+")) return monedes + Integer.parseInt(accio.substring(1));
            if (accio.contains("-")) return monedes - Integer.parseInt(accio.substring(1));
            if (accio.contains("*")) return monedes * Integer.parseInt(accio.substring(1));
            if (accio.contains("/")) return monedes / Integer.parseInt(accio.substring(1));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return monedes;
    }
    private void afegirEsdevenimentCalendari() {
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, "Victòria a Ruletilla!")
                .putExtra(CalendarContract.Events.DESCRIPTION, "Has aconseguit el límit de monedes jugant.")
                .putExtra(CalendarContract.Events.EVENT_LOCATION, "Ruletilla App")
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 60 * 60 * 1000); // 1 hora

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "No s'ha trobat cap aplicació de calendari.", Toast.LENGTH_SHORT).show();
        }
    }
    private void guardarUbicacio(double latitud, double longitud) {
        MonedaDatabaseHelper dbHelper = new MonedaDatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("latitud", latitud);
        values.put("longitud", longitud);
        values.put("timestamp", System.currentTimeMillis());

        long newRowId = db.insert("ubicacions", null, values);
        if (newRowId != -1) {
            Log.d("UBICACIO", "Ubicació guardada amb ID: " + newRowId);
        } else {
            Log.e("UBICACIO", "Error en guardar la ubicació");
        }

        db.close();
    }
    private void guardarCapturaPantalla() {
        try {
            // Captura la vista completa de la pantalla
            View vista = getWindow().getDecorView().getRootView();
            vista.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(vista.getDrawingCache());
            vista.setDrawingCacheEnabled(false);

            // Guarda la imatge en el directori Pictures
            String nomFitxer = "victoria_ruleta_" + System.currentTimeMillis() + ".png";
            OutputStream fos;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, nomFitxer);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

                ContentResolver resolver = getContentResolver();
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                fos = resolver.openOutputStream(uri);
            } else {
                File directorio = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File fitxer = new File(directorio, nomFitxer);
                fos = new FileOutputStream(fitxer);
            }

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            Toast.makeText(this, "Captura desada a la galeria 🎉", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error en desar la captura 😢", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!canviIntern) {
            soundManager.pauseMusic(); // pausa la música si l'app passa a segon pla
        }
        canviIntern = false;
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d("CICLE_VIDA", "onResume cridat");
        actualitzarNomUsuari();
        SharedPreferences prefs = getSharedPreferences("configuracio_joc", MODE_PRIVATE);
        String uriActual = prefs.getString("musica_personalitzada", null);
        String uriUltim = soundManager.getLastMusicUri();

        if (!soundManager.isPlaying()) {
            soundManager.playBackgroundMusic();
        } else if (uriActual != null && !uriActual.equals(uriUltim)) {
            soundManager.playBackgroundMusic(); // cançó nova seleccionada → la carreguem
        } else if (!canviIntern) {
            soundManager.resumeMusic(); // només si venim de fora de l'app
        }

        canviIntern = false; // restablim l'estat
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        SoundManager.getInstance(this).releaseAll();
    }
    protected void onStop() {
        super.onStop();
        if (soundManager != null) {
            soundManager.pauseMusic();  // o stopMusic() si vols aturar completament
        }
    }
    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w("Login", "Google sign in failed", e);
            }
        }
    }
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(this, "Login correcte: " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Error d'autenticació", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void actualitzarNomUsuari() {
        TextView txtUsuari = findViewById(R.id.txtUsuari);
        FirebaseUser usuariActual = FirebaseAuth.getInstance().getCurrentUser();

        if (usuariActual != null) {
            String nom = usuariActual.getDisplayName();
            String email = usuariActual.getEmail();
            Log.d("LOGIN", "Usuari trobat: " + nom + ", email: " + email);

            if (nom != null && !nom.isEmpty()) {
                txtUsuari.setText("Benvingut, " + nom + "!");
            } else {
                txtUsuari.setText("Benvingut!");
            }
        } else {
            Log.d("LOGIN", "usuariActual és NULL");
            txtUsuari.setText(""); // no mostrem res
        }
    }
}
