package www.ghelper.org.guitarhelperandroid;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.herac.tuxguitar.io.gtp.GP1InputStream;
import org.herac.tuxguitar.io.gtp.GP2InputStream;
import org.herac.tuxguitar.io.gtp.GP3InputStream;
import org.herac.tuxguitar.io.gtp.GP4InputStream;
import org.herac.tuxguitar.io.gtp.GP5InputStream;
import org.herac.tuxguitar.io.gtp.GTPInputStream;
import org.herac.tuxguitar.io.gtp.GTPSettings;
import org.herac.tuxguitar.song.factory.TGFactory;
import org.herac.tuxguitar.song.models.TGBeat;
import org.herac.tuxguitar.song.models.TGMeasure;
import org.herac.tuxguitar.song.models.TGSong;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GearTransfer";
    private static final Class<ServiceConnection> SASOCKET_CLASS = ServiceConnection.class;
    private String SRC_PATH=Environment.getExternalStorageDirectory()+"/GuitarHelper/";

    private ListView fFind_ListView;
    private ProgressBar mSentProgressBar;
    private Context mCtxt;
    private String mDirPath;
    private long currentTransId;
    private long mFileSize;
    private List<Long> mTransactions = new ArrayList<Long>();
    private FileTransferSender mSenderService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "Service disconnected");
            mSenderService = null;
        }

        @Override
        public void onServiceConnected(ComponentName arg0, IBinder binder) {
            Log.d(TAG, "Service connected");
            mSenderService = ((FileTransferSender.SenderBinder) binder).getService();
            mSenderService.registerFileAction(getFileAction());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCtxt = getApplicationContext();
        fFind_ListView = (ListView)findViewById(R.id.Find_ListView);
        fFind_ListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(mCtxt, " 외장메모리가 존재하지 않습니다.", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            mDirPath = Environment.getExternalStorageDirectory() + File.separator + "GuitarHelper"+File.separator;
            File file = new File(mDirPath);
            if (file.mkdirs()) {
                Toast.makeText(mCtxt, "외장메모리 존재" + mDirPath, Toast.LENGTH_LONG).show();
            }
        }
        mCtxt.bindService(new Intent(getApplicationContext(), FileTransferSender.class),
                this.mServiceConnection, Context.BIND_AUTO_CREATE);

        findFolder();

        mSentProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mSentProgressBar.setMax(100);
    }
    private void findFolder(){
        ArrayList<String> fName = new ArrayList<String>();
        File files = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/GuitarHelper");
        //if(!files.exists()) files.mkdir();
        ArrayAdapter<String> filelist = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice,fName);
        if(files.listFiles().length>0){
            for(File file : files.listFiles()){
                fName.add(file.getName());
            }
        }
        files = null;
        fFind_ListView.setAdapter(filelist);
    }
    public void connectBtClicked(View v){
        if (mSenderService != null) {
            mSenderService.connect();
        } else {
            Toast.makeText(getApplicationContext(), "기어를 연결하세요~", Toast.LENGTH_SHORT).show();
        }
    }
    public void sendBtClicked(View v){
        String filename=(String)fFind_ListView.getItemAtPosition(fFind_ListView.getCheckedItemPosition());
        String src=mDirPath+filename;

        String ext=src.substring(src.lastIndexOf(".")+1, src.length());

        TGSong song=null;
        try {
            InputStream inputStream = new FileInputStream(src);
            BufferedInputStream stream= new BufferedInputStream(inputStream);

            GTPInputStream reader=null;

            switch(ext) {
                case "gp1":
                    reader = new GP1InputStream(new GTPSettings());
                    break;
                case "gp2":
                    reader = new GP2InputStream(new GTPSettings());
                    break;
                case "gp3":
                    reader = new GP3InputStream(new GTPSettings());
                    break;
                case "gp4":
                    reader = new GP4InputStream(new GTPSettings());
                    break;
                case "gp5":
                    reader = new GP5InputStream(new GTPSettings());
                    break;
                default:

                    break;
            }
            if(reader!=null){
                reader.init(new TGFactory(),stream);
                if(reader.isSupportedVersion()){
                    song=reader.readSong();
                }
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        PrintWriter pw=null;
        try {
            pw = new PrintWriter(new FileOutputStream(src.substring(0, src.lastIndexOf(".")+1)+"ghs"));
            Iterator<TGMeasure> it=song.getTrack(0).getMeasures();
            pw.write(song.getMeasureHeader(0).getTempo().getValue()+"\r\n");

            while(it.hasNext()){
                TGMeasure tgMeasure=(TGMeasure)it.next();

                if(tgMeasure!=null){
                    List<TGBeat> list = tgMeasure.getBeats();
                    String before="X";
                    Iterator<TGBeat> it2 =list.iterator();
                    while(it2.hasNext()){
                        TGBeat beat=(TGBeat)it2.next();
                        try{
                            if(beat.isRestBeat()){
                                pw.write("MUTE,");
                            }
                            else if(beat.getText()==null){
                                pw.write(before+",");
                            }
                            else{
                                pw.write(beat.getText().getValue()+",");
                                before=beat.getText().getValue();
                            }
                            pw.write(beat.getVoice(0).getDuration().getTime()+"\r\n");
                        }catch(NullPointerException e){
                            System.out.println(e.getMessage());
                        }
                    }
                }
            }
            pw.close();
        } catch (FileNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        Toast.makeText(mCtxt, src.substring(0, src.lastIndexOf(".")+1)+"ghs", Toast.LENGTH_SHORT).show();
        //String filename=(String)fFind_ListView.getItemAtPosition(fFind_ListView.getCheckedItemPosition());
        if (isSenderServiceBound()) {
            try {
                int trId = mSenderService.sendFile(src.substring(0, src.lastIndexOf(".")+1)+"ghs");
                mTransactions.add((long) trId);
                currentTransId = trId;
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                Toast.makeText(mCtxt, "IllegalArgumentException", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onDestroy() {
        getApplicationContext().unbindService(mServiceConnection);
        super.onDestroy();
    }

    private FileTransferSender.FileAction getFileAction() {
        return new FileTransferSender.FileAction() {
            @Override
            public void onFileActionError() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSentProgressBar.setProgress(0);
                        mTransactions.remove(currentTransId);
                        Toast.makeText(mCtxt, "에러!", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFileActionProgress(final long progress) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSentProgressBar.setProgress((int) progress);
                    }
                });
            }

            @Override
            public void onFileActionTransferComplete() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSentProgressBar.setProgress(0);
                        mTransactions.remove(currentTransId);
                        Toast.makeText(mCtxt, "전송완료!", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFileActionCancelAllComplete() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSentProgressBar.setProgress(0);
                        mTransactions.remove(currentTransId);
                    }
                });
            }
        };
    }
    private boolean isSenderServiceBound() {
        return this.mSenderService != null;
    }
}
