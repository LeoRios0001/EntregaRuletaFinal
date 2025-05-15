package com.example.ruletaapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class TopMundialActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TopPuntuacionsAdapter adapter;
    private List<Puntuacio> topPuntuacions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_top_mundial);

        recyclerView = findViewById(R.id.recyclerTopMundial);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TopPuntuacionsAdapter(topPuntuacions);
        recyclerView.setAdapter(adapter);

        Button btnTornar = findViewById(R.id.btnTornarTop);
        btnTornar.setOnClickListener(v -> finish());

        carregarTopPuntuacionsAmbRest();
    }

    private void carregarTopPuntuacionsAmbRest() {
        new Thread(() -> {
            try {
                FirestoreResponse resposta = RetrofitClient.getApi().getPuntuacionsFirestore().execute().body();
                if (resposta != null) {
                    List<Puntuacio> llista = FirestoreMapper.convertirResposta(resposta);
                    Collections.sort(llista, (p1, p2) -> Integer.compare(p2.getMonedes(), p1.getMonedes()));

                    runOnUiThread(() -> {
                        topPuntuacions.clear();
                        int limit = Math.min(10, llista.size());
                        topPuntuacions.addAll(llista.subList(0, limit));
                        adapter.notifyDataSetChanged();
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Resposta buida de Firebase", Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error carregant puntuacions REST", Toast.LENGTH_SHORT).show();
                    Log.e("RETROFIT", "Error REST", e);
                });
            }
        }).start();
    }

}
