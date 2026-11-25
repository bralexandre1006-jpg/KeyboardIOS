package com.nomad.ioskeyboard;

import android.service.textservice.SpellCheckerService;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

/** Reports every word as correct so apps never show the red underline / Android popup; fixes happen in the keyboard bar. */
public class SpellChecker extends SpellCheckerService {
    @Override
    public Session createSession() {
        return new Session() {
            @Override public void onCreate() { }
            @Override public SuggestionsInfo onGetSuggestions(TextInfo info, int limit) {
                return new SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, new String[0], info.getCookie(), info.getSequence());
            }
        };
    }
}
