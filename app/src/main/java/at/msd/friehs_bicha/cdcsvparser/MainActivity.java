package at.msd.friehs_bicha.cdcsvparser;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;

import at.msd.friehs_bicha.cdcsvparser.General.AppModel;


public class MainActivity extends AppCompatActivity {

    private static final int PICKFILE_REQUEST_CODE = 1;
    Context context;
    AppModel appModel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getApplicationContext();

        //get the spinner from the xml.
        Spinner dropdown = findViewById(R.id.spinner_history);
        //create a list of items for the spinner.
        String[] items = new String[]{"1", "2", "three"};
        //create an adapter to describe how the items are displayed, adapters are used in several places in android.
//There are multiple variations of this, but this is the basic variant.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
//set the spinners adapter to the previously created one.
        dropdown.setAdapter(adapter);

        Button btnParse = findViewById(R.id.btn_parse);
        Button btnHistory = findViewById(R.id.btn_history);

        btnParse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBtnClick(view);
            }

        });
        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, ParseActivity.class);
                startActivity(intent);
            }

        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICKFILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Get the URI of the selected file
            Uri fileUri = data.getData();
            long time = System.currentTimeMillis();
            String filename = Long.toString(time) + ".csv";
            ArrayList<String> list = getFileContentFromUri(fileUri);

            try (FileOutputStream fos = context.openFileOutput(filename, Context.MODE_APPEND)) {
                for (String element : list) {
                    fos.write(element.getBytes());
                    fos.write("\n".getBytes());  // add a newline after each element
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println(filename + "\n");
            System.out.println(context.getFilesDir() + "\n");
            System.out.println(new File(context.getFilesDir(), filename) + "\n");



            appModel = new AppModel(list);


            callParseView();
        }
    }

    private void callParseView() {
        Intent intent = new Intent(MainActivity.this, ParseActivity.class);
        intent.putExtra("AppModel", appModel);
        startActivity(intent);
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