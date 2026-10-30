package local.mediacentre.client.ui;

import android.app.Activity;
import android.widget.EditText;
import android.view.View;
import local.mediacentre.client.R;

/** Exercises unsaved form state across palette changes. Never exports connection values. */
public final class SettingsDraftProbe {
    private static String address,code;
    private static int start,end;
    private static boolean focused;
    private static EditText editor(Activity a,String id){View v=PanelStyle.find(a.findViewById(R.id.content),id);if(!(v instanceof EditText))throw new IllegalStateException("Open settings first");return (EditText)v;}
    public static void run(Activity a,String action){
        EditText target=editor(a,"pair_address"),secret=editor(a,"pair_code");
        if("prepare".equals(action)){
            if(address!=null)throw new IllegalStateException("Draft already prepared");
            address=target.getText().toString();code=secret.getText().toString();focused=target.hasFocus();start=target.getSelectionStart();end=target.getSelectionEnd();
            target.setText("https://draft.invalid:8445");secret.setText("TEST1234");target.requestFocus();target.setSelection(3,8);return;
        }
        if(!"verify".equals(action)||address==null)throw new IllegalArgumentException("Prepare draft first");
        try{if(!"https://draft.invalid:8445".equals(target.getText().toString())||!"TEST1234".equals(secret.getText().toString())||!target.hasFocus()||target.getSelectionStart()!=3||target.getSelectionEnd()!=8)throw new IllegalStateException("Unsaved form state changed");}
        finally{target.setText(address);secret.setText(code);if(focused){target.requestFocus();target.setSelection(Math.max(0,Math.min(start,target.length())),Math.max(0,Math.min(end,target.length())));}else target.clearFocus();address=code=null;}
    }
}
