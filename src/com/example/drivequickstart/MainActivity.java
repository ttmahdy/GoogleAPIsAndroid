package com.example.drivequickstart;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.example.drivequickstart.MultipartPost.MultipartPost;
import com.example.drivequickstart.MultipartPost.PostParameter;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;


public class MainActivity extends Activity {
  static final int REQUEST_ACCOUNT_PICKER = 1;
  static final int REQUEST_AUTHORIZATION = 2;
  static final int CAPTURE_IMAGE = 3;

  private static Uri fileUri;
  private static Drive service;
  private GoogleAccountCredential credential;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // had to update this part to a collection of String objects instead of a single String obj ref
    // also tested updating DRIVE to DRIVE_FILE both seem to work fine though the more specific the better
    credential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(new String[]{DriveScopes.DRIVE}));
    startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);

    // check readme for other changes
  
  }

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    switch (requestCode) {
    case REQUEST_ACCOUNT_PICKER:
      if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
        String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        if (accountName != null) {
          credential.setSelectedAccountName(accountName);
          service = getDriveService(credential);
          startCameraIntent();
        }
      }
      break;
    case REQUEST_AUTHORIZATION:
      if (resultCode == Activity.RESULT_OK) {
        saveFileToDrive();
      } else {
        startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
      }
      break;
    case CAPTURE_IMAGE:
      if (resultCode == Activity.RESULT_OK) {
        saveFileToDrive();
      }
    }
  }

  private void startCameraIntent() {
    String mediaStorageDir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES).getPath();
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    fileUri = Uri.fromFile(new java.io.File(mediaStorageDir + java.io.File.separator + "IMG_"
        + timeStamp + ".jpg"));

    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
    startActivityForResult(cameraIntent, CAPTURE_IMAGE);
  }

  private void saveFileToDrive() {
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          // File's binary content
          java.io.File fileContent = new java.io.File(fileUri.getPath());
          FileContent mediaContent = new FileContent("image/jpeg", fileContent);

          // File's metadata.
          File body = new File();
          body.setTitle(fileContent.getName());
          body.setMimeType("image/jpeg");

          // File file = service.files().insert(body, mediaContent).execute();
          // update required for ocr
          File file = service.files().insert(body, mediaContent).setOcr(true).setOcrLanguage("en").execute();
          
          if (file != null) {
        	String stringInImage = getCharactersFromImage(file);
        	showToast("Photo uploaded: " + stringInImage);

        	DisplayMessage(stringInImage, "the OCR in image");
        	
        	java.io.File localImageFile = new java.io.File(fileUri.getPath());
            String imageSearchResult = getResult(localImageFile);
            showToast("You are looking at: " + imageSearchResult);
        	
            DisplayMessage(imageSearchResult, "the OCR in image");
            
        	if (file.getIndexableText() != null)
            {	
        	  String ocrString = file.getIndexableText().getText();
        	  showToast("Photo OCR: " + ocrString);
            }
            showToast("Photo uploaded: " + file.getTitle());
            startCameraIntent();
          }
        } catch (UserRecoverableAuthIOException e) {
          startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
    t.start();
  }
  
  public static String getResult(java.io.File file){
		String tempresult = null;
		String result2 = sendPostRequest(file);
		boolean best_guess_found=false;

		StringTokenizer tok = new StringTokenizer(result2, "<>");
		String previous_entity=null;
		
		while(tok.hasMoreElements()){
			String nextitem = tok.nextElement().toString();
		
			if (best_guess_found==false && nextitem.startsWith("Best guess for this image")){
				Log.d("Tokenizer", nextitem);
				best_guess_found=true;
			} else if (best_guess_found==true && nextitem.contains("q=") && nextitem.contains("&amp")){
				int start = nextitem.indexOf("q=")+2;
				int end = nextitem.indexOf("&amp", start);
				String contents = previous_entity.substring( start , end);
				contents = contents.replace('+', ' ');
				Log.d("Result:", contents);
				
				tempresult = contents;
				break;
			} else if(nextitem.startsWith("Visually similar") && best_guess_found==false){
				Log.d("Tokenizer", "nextitem: " + nextitem + " previousitem: " + previous_entity);
				try{
					if(previous_entity.contains("q=") && previous_entity.contains("&amp")){
						int start = previous_entity.indexOf("q=")+2;
						int end = previous_entity.indexOf("&amp", start);
						String contents = previous_entity.substring( start , end);
						contents = contents.replace('+', ' ');
					
						Log.d("Result:", contents);
				
						tempresult = contents;
					} else {
						
						
					}
				} catch (Exception e){
					e.printStackTrace();						
				}
				break;
			}
			
			if(nextitem.startsWith("a")){
				StringTokenizer tok2 = new StringTokenizer(nextitem);
				
				while(tok2.hasMoreElements()){
					String subitem = tok2.nextElement().toString();
					if( subitem.startsWith("href") ){
						previous_entity=nextitem;
					}
				}
			}
		}
		
		return tempresult;
	}

  private Drive getDriveService(GoogleAccountCredential credential) {
    return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
        .build();
  }

  private static String sendPostRequest(java.io.File filename) {
	  	String response = null;
	  	
	  	Log.d("SendPostRequest", "sendPostRequest");
	  	@SuppressWarnings("rawtypes")
			List<PostParameter> params = new ArrayList<PostParameter>();
	  	params.add(new PostParameter<String>("image_url", ""));
	  	params.add(new PostParameter<String>("btnG", "Search"));
	  	params.add(new PostParameter<String>("image_content", ""));
	  	params.add(new PostParameter<String>("filename", ""));
	  	params.add(new PostParameter<String>("hl", "en"));
	  	params.add(new PostParameter<String>("safe", "off"));
	  	params.add(new PostParameter<String>("bih", ""));
	  	params.add(new PostParameter<String>("biw", ""));
	  	params.add(new PostParameter<java.io.File>("encoded_image", filename));
	  	
	  	try {
	  		Log.d("INSTANT", "multipart post created");
	  		MultipartPost post = new MultipartPost(params);
	  		Log.d("INSTANT", "multipart post created");
	  		response = post.send("http://www.google.com/searchbyimage/upload", "http://images.google.com");
	    		
	  	} catch (Exception e) {
	  		Log.e("INSTANT", "Bad Post", e);
		}
	  	
	  	params.clear();
	  	params = null;
	  	return response;
	  }  
  
  @SuppressWarnings({ "finally", "unused" })
private String getCharactersFromImage(File imagefile) throws IOException {
		
	  String imageAsTextUrl = imagefile.getExportLinks().get("text/plain");
	  BufferedReader in = null;	
      String stringInImage = null;
      StringBuffer sb = new StringBuffer();
      
	  try {
		
		  HttpResponse resp = service.getRequestFactory().buildGetRequest(new GenericUrl(imageAsTextUrl)).execute();
		  in = new BufferedReader(new InputStreamReader(resp.getContent()));

          while ((stringInImage = in.readLine()) != null) {
        	  sb.append(stringInImage);
          }
        }
        finally {
            if (in != null) {
                in.close();
            }
            return sb.toString();
        }
	  }

  public void DisplayMessage (final String message, final String title ) {

	  AlertDialog.Builder builder = new AlertDialog.Builder(this);
	  builder.setMessage(message)
	         .setCancelable(false)
	         .setPositiveButton("OK", new DialogInterface.OnClickListener() {
	             public void onClick(DialogInterface dialog, int id) {
	                  //do things
	             }
	         });
	  AlertDialog alert = builder.create();
	  alert.show();
	  
  }
  
  public void showToast(final String toast) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT).show();
      }
    });
  }
}
