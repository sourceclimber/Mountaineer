package at.fhooe.mos.mountaineer.ui;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

public class IntEditTextPreference extends EditTextPreference {

    public IntEditTextPreference(Context context) {
        super(context);
    }

    public IntEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IntEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected String getPersistedString(String defaultReturnValue) {
        return String.valueOf(super.getPersistedInt(-1));
    }

    @Override
    protected boolean persistString(String value) {
        return super.persistInt(Integer.valueOf(value));
    }
}
