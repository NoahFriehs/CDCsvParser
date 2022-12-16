package at.msd.friehs_bicha.cdcsvparser;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import at.msd.friehs_bicha.cdcsvparser.general.AppModel;


public class MainActivity extends AppCompatActivity {

    private static final int PICKFILE_REQUEST_CODE = 1;
    Context context;
    AppModel appModel;
    File[] files;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getApplicationContext();
        //get the elements from the xml.
        Spinner dropdown = findViewById(R.id.spinner_history);
        Button btnParse = findViewById(R.id.btn_parse);
        Button btnHistory = findViewById(R.id.btn_history);

        btnParse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBtnUploadClick(view);
            }

        });

        updateFiles();

        if(files.length == 0){
            // Disable the button
            // Get the app's resources
            Resources res = getResources();
            // Get the drawable with the name "my_drawable" from the app's resources
            Drawable drawable = res.getDrawable(R.drawable.round_button_disabeld);
            btnHistory.setEnabled(false);
            btnHistory.setBackgroundColor(Color.LTGRAY);
            btnHistory.setTextColor(Color.DKGRAY);
            btnHistory.setBackground(drawable);
        }else{
            setSpinner(dropdown);


            btnHistory.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBtnHistoryClick(view, files,dropdown);
                }

            });
        }



    }

    @Override
    protected void onRestart() {
        super.onRestart();

        updateFiles();
        Spinner dropdown = findViewById(R.id.spinner_history);
        setSpinner(dropdown);
    }

    private void updateFiles(){
        // Get the app's internal file directory
        File appDir = getFilesDir();
        // Get a list of all files in the app's internal file directory
        files = appDir.listFiles();
    }

    private void setSpinner(Spinner spinner){
        String[] fileNames = new String[files.length];
        SimpleDateFormat sdf = new SimpleDateFormat("M-d-yyyy-hh-mm-ss");
        SimpleDateFormat dateFormat = new SimpleDateFormat("d.M hh:mm");
        String filename;
        Date date;
        for (int i = 0; i < files.length;i++) {
            filename = files[i].getName();
            filename = filename.substring(0, filename.length() - 4);
            try {
                date = sdf.parse(filename);
                filename = dateFormat.format(date);
            } catch (ParseException e) {
                e.printStackTrace();
                // TODO error message
            }
            fileNames[i] = filename;
        }

        //create an adapter to describe how the items are displayed, adapters are used in several places in android.
        ArrayAdapter<String> fileNamesAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, fileNames);
        //set the spinners adapter to the previously created one.
        spinner.setAdapter(fileNamesAdapter);

    }

    private void onBtnHistoryClick(View view, File[] files, Spinner spinner) {
        int position = spinner.getSelectedItemPosition();
        File selectedFile = files[position];

        ArrayList<String> list = getFileContent(selectedFile);

        try {
            appModel = new AppModel(list);
            callParseView();
        }catch (Exception e) {
            CharSequence text = e.getMessage();
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICKFILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Get the URI of the selected file
            Uri fileUri = data.getData();
            //creat filename with format M-d-y-H-m-s
            SimpleDateFormat dateFormat = new SimpleDateFormat("M-d-y-H-m-s");
            Date now = new Date();
            String time = dateFormat.format(now);
            String filename = time + ".csv";

            ArrayList<String> list = getFileContentFromUri(fileUri);
//TODO set timezone
            try (FileOutputStream fos = context.openFileOutput(filename, Context.MODE_APPEND)) {
                for (String element : list) {
                    fos.write(element.getBytes());
                    fos.write("\n".getBytes());  // add a newline after each element
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            //delete oldest file if alredy 7 files in array
            updateFiles();
            while(files.length > 7){
                files[0].delete();
                updateFiles();
            }

            try {
                appModel = new AppModel(list);
                callParseView();
            }catch (IllegalArgumentException e) {
                context = getApplicationContext();
                CharSequence text = e.getMessage();
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();

            }catch (RuntimeException e){
                Context context = getApplicationContext();
                CharSequence text = e.getMessage();
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
                callParseView();
            }


        }
    }

    private void callParseView() {
        Intent intent = new Intent(MainActivity.this, ParseActivity.class);
        intent.putExtra("AppModel", appModel);
        startActivity(intent);
    }

    public void onBtnUploadClick(View view){
        // Create an Intent object to allow the user to select a file
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);

        // Set the type of file that the user can select
        chooseFile.setType("*/*");

        // Start the activity to let the user select a file
        startActivityForResult(chooseFile, PICKFILE_REQUEST_CODE);
    }

    public ArrayList<String> getFileContentFromUri(Uri uri){
        ArrayList<String> fileContents = new ArrayList<>();
        try {
            // Get the ContentResolver for the current context.
            ContentResolver resolver = getContentResolver();

            // Open an InputStream for the file represented by the Uri.
            InputStream inputStream = resolver.openInputStream(uri);

            // Create a BufferedReader to read the file contents.
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            // Read the file line by line and add each line to the fileContents list.
            String line;
            while ((line = reader.readLine()) != null) {
                fileContents.add(line);
            }

            // Close the BufferedReader and InputStream.
            reader.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileContents;
    }
    public ArrayList<String> getFileContent(File file){
        ArrayList<String> fileContents = new ArrayList<>();
        try {

            // Create a BufferedReader to read the file contents.
            BufferedReader reader = new BufferedReader(new FileReader(file));

            // Read the file line by line and add each line to the fileContents list.
            String line;
            while ((line = reader.readLine()) != null) {
                fileContents.add(line);
            }

            // Close the BufferedReader and InputStream.
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileContents;
    }

}