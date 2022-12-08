package at.msd.friehs_bicha.cdcsvparser;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import at.msd.friehs_bicha.cdcsvparser.General.AppModel;


public class MainActivity extends AppCompatActivity {

    private static final int PICKFILE_REQUEST_CODE = 1;

    AppModel appModel = new AppModel();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //get the spinner from the xml.
        Spinner dropdown = findViewById(R.id.spinner1);
//create a list of items for the spinner.
        String[] items = new String[]{"1", "2", "three"};
//create an adapter to describe how the items are displayed, adapters are used in several places in android.
//There are multiple variations of this, but this is the basic variant.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
//set the spinners adapter to the previously created one.
        dropdown.setAdapter(adapter);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICKFILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Get the URI of the selected file
            Uri fileUri = data.getData();

            ArrayList<String> list = getFileContentFromUri(fileUri);
            appModel.init(list);

        }
    }

    public void onBtnClick(View view){
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

}