package fr.enseeiht.edimaria.videoplayer;

import androidx.appcompat.app.AppCompatActivity;
import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    /**
     * Gestion de la progression de la barre de recherche en temps réel.
     */
    private class BarUpdaterTask extends AsyncTask {
        private static final String TAG = "BUT: ";
        @Override
        protected Object doInBackground(Object[] objects) {
            while (MainActivity.this.videoPlayer != null) {
                publishProgress(MainActivity.this.videoPlayer.getCurrentPosition(), MainActivity.this.videoPlayer.getDuration());
                try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
            }
            return null;
        }
        @Override
        protected void onProgressUpdate(Object[] objects) {
            float position = (int) objects[0];
            float duration = (int) objects[1];
            float progress = position / duration * 100;
            seekBar.setProgress((int) progress);
        }
    }

    private static final String TAG = "Video player activity: ";
    private static final int SELECT_VIDEO = 100;
    private Button importButton;
    private Button playButton;
    private Button startButton;
    private Button pauseButton;
    private Uri uri;
    private SurfaceView videoSurfaceView;
    private TextView uriTextView;
    private SurfaceHolder holder;
    private SeekBar seekBar;
    private MediaPlayer videoPlayer = new MediaPlayer();
    private BarUpdaterTask barUpdaterTask;
    private boolean firstVideoIsSelectedFlag = false;
    private boolean newUriFlag = false;
    private int currentPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        importButton = (Button) findViewById(R.id.id_selectFromGallery);
        playButton = (Button) findViewById(R.id.id_intentPlay);
        startButton = (Button) findViewById(R.id.id_start);
        pauseButton = (Button) findViewById(R.id.id_resume);
        videoSurfaceView = (SurfaceView) findViewById(R.id.id_video);
        uriTextView = (TextView) findViewById(R.id.id_url);
        seekBar = (SeekBar) findViewById(R.id.id_seekBar);

        // Boutons inaccessibles avant le choix de la vidéo
        playButton.setEnabled(false);
        startButton.setEnabled(false);
        pauseButton.setEnabled(false);
        seekBar.setVisibility(View.INVISIBLE);

        // Initialisation des surfaces pour MediaPlayer
        holder = videoSurfaceView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // Importation de la vidéo
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Intent.ACTION_PICK);
                i.setDataAndType(Uri.parse("/storage/self/"), "video/*");
                startActivityForResult(i, SELECT_VIDEO);
            }
        });

        // Lecture de la vidéo sur une application externe
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        });

        // Lancement de la vidéo au sein de l'application
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!firstVideoIsSelectedFlag) {
                    try { videoPlayer.setDataSource(MainActivity.this, uri); } catch (IOException e) { e.printStackTrace(); }
                    firstVideoIsSelectedFlag = true;
                } else
                    videoPlayer.stop();
                try { videoPlayer.prepare(); } catch (IOException e) { e.printStackTrace(); }
                videoPlayer.start();
            }
        });

        // Pause/reprise de la vidéo au sein de l'application
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!firstVideoIsSelectedFlag) {
                    try { videoPlayer.setDataSource(MainActivity.this, uri); } catch (IOException e) { e.printStackTrace(); }
                    firstVideoIsSelectedFlag = true;
                    try { videoPlayer.prepare(); } catch (IOException e) { e.printStackTrace(); }
                    videoPlayer.start();
                } else if (videoPlayer.isPlaying())
                    videoPlayer.pause();
                else
                    videoPlayer.start();
            }
        });

        // Modification du curseur de la barre de recherche
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && MainActivity.this.videoPlayer != null) {
                    Log.d(TAG, "B");
                    videoPlayer.seekTo((int) ((float) progress / 100f * (float) MainActivity.this.videoPlayer.getDuration()));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    /**
     * Récupération de l'URL de la vidéo sélectionnée et activation des boutons de contrôle de la vidéo.
     */
    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == SELECT_VIDEO) {
            uri = data.getData();
            uriTextView.setText(uri.toString());
            playButton.setEnabled(true); startButton.setEnabled(true); pauseButton.setEnabled(true);
            seekBar.setVisibility(View.VISIBLE);
            if (firstVideoIsSelectedFlag) newUriFlag = true;
        }
    }

    /**
     * Fonctions implémentant l'interface SurfaceHolder.Callback.
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) { videoPlayer.setDisplay(holder); }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) { }

    /**
     * Mise à zéro du Holder et du MediaPlayer lorsque l'application
     * est mise en tâche de fond. On sauvegarde l'avancement.
     */
    public void onPause() {
        if (firstVideoIsSelectedFlag) {
            currentPosition = videoPlayer.getCurrentPosition();
            holder.removeCallback(this);
            videoPlayer.stop(); videoPlayer.reset(); videoPlayer.release(); videoPlayer = null;
        }
        barUpdaterTask = null;
        super.onPause();
    }

    /**
     * Réinitialisation du Holder et du MediaPlayer quand l'application
     * revient au premier plan.
     *  + currentPosition' = 0 si une nouvelle vidéo est lancée
     *  + currentPosition' = currentPosition sinon
     */
    public void onResume() {
        if (firstVideoIsSelectedFlag) {
            videoPlayer = new MediaPlayer();
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            try { videoPlayer.setDataSource(this, uri); } catch (IOException e) { e.printStackTrace(); }
            try { videoPlayer.prepare(); } catch (IOException e) { e.printStackTrace(); }
            if (newUriFlag) {
                currentPosition = 0;
                newUriFlag = false;
            }
            videoPlayer.seekTo(currentPosition);
        }
        barUpdaterTask = new BarUpdaterTask();
        seekBar.setProgress(0);
        barUpdaterTask.execute();
        super.onResume();
    }
}