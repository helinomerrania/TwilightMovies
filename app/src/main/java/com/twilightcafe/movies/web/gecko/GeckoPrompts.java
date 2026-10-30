package com.twilightcafe.movies.web.gecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

import java.util.ArrayList;
import java.util.List;

/**
 * The page's own dialogs in Firefox's engine, shown as the box's own engine shows them: a list to pick from
 * (a select), a message (alert) and a question (confirm), each moved through with the remote. Leaving a page is
 * never asked about, a window the engine stopped stays stopped, and anything else (sign-in, files) is refused.
 */
final class GeckoPrompts implements GeckoSession.PromptDelegate {
    private final Context ctx;

    GeckoPrompts(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession s, final ChoicePrompt p) {
        final List<ChoicePrompt.Choice> list = new ArrayList<ChoicePrompt.Choice>();
        flatten(p.choices, list);
        if (list.isEmpty() || !showable()) return GeckoResult.fromValue(p.dismiss());
        final Answer a = new Answer(p);
        String[] labels = new String[list.size()];
        final boolean[] picked = new boolean[list.size()];
        int chosen = -1;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = list.get(i).label;
            picked[i] = list.get(i).selected;
            if (picked[i] && chosen < 0) chosen = i;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(ctx).setTitle(p.title);
        if (p.type == ChoicePrompt.Type.MULTIPLE) {
            b.setMultiChoiceItems(labels, picked, (d, which, on) -> picked[which] = on);
            b.setPositiveButton(android.R.string.ok, (d, w) -> {
                List<ChoicePrompt.Choice> on = new ArrayList<ChoicePrompt.Choice>();
                for (int i = 0; i < picked.length; i++) if (picked[i]) on.add(list.get(i));
                a.give(p.confirm(on.toArray(new ChoicePrompt.Choice[0])));
            });
        } else {
            b.setSingleChoiceItems(labels, chosen, (d, which) -> {
                a.give(p.confirm(list.get(which)));
                d.dismiss();
            });
        }
        return a.show(b);
    }

    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession s, final AlertPrompt p) {
        if (!showable()) return GeckoResult.fromValue(p.dismiss());
        Answer a = new Answer(p);
        return a.show(new AlertDialog.Builder(ctx).setTitle(p.title).setMessage(p.message)
                .setPositiveButton(android.R.string.ok, null));
    }

    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession s, final ButtonPrompt p) {
        if (!showable()) return GeckoResult.fromValue(p.dismiss());
        final Answer a = new Answer(p);
        return a.show(new AlertDialog.Builder(ctx).setTitle(p.title).setMessage(p.message)
                .setPositiveButton(android.R.string.ok, (d, w) -> a.give(p.confirm(ButtonPrompt.Type.POSITIVE)))
                .setNegativeButton(android.R.string.cancel, (d, w) -> a.give(p.confirm(ButtonPrompt.Type.NEGATIVE))));
    }

    @Override
    public GeckoResult<PromptResponse> onBeforeUnloadPrompt(GeckoSession s, BeforeUnloadPrompt p) {
        return GeckoResult.fromValue(p.confirm(AllowOrDeny.ALLOW));
    }

    @Override
    public GeckoResult<PromptResponse> onPopupPrompt(GeckoSession s, PopupPrompt p) {
        return GeckoResult.fromValue(p.confirm(AllowOrDeny.DENY));
    }

    private boolean showable() {
        return !(ctx instanceof Activity) || !((Activity) ctx).isFinishing();
    }

    /** The choices to pick from: those in groups too; not separators, nor those the page turned off. */
    private static void flatten(ChoicePrompt.Choice[] choices, List<ChoicePrompt.Choice> out) {
        if (choices == null) return;
        for (ChoicePrompt.Choice c : choices) {
            if (c.items != null) flatten(c.items, out);
            else if (!c.separator && !c.disabled) out.add(c);
        }
    }

    /** One dialog's answer to the engine: what was chosen, or, when it closes any other way, nothing. */
    private final class Answer {
        final BasePrompt prompt;
        final GeckoResult<PromptResponse> result = new GeckoResult<PromptResponse>();
        boolean given;

        Answer(BasePrompt prompt) {
            this.prompt = prompt;
        }

        void give(PromptResponse r) {
            if (given) return;
            given = true;
            result.complete(r);
        }

        GeckoResult<PromptResponse> show(AlertDialog.Builder b) {
            final AlertDialog d = b.setOnDismissListener(x -> {
                if (!given) give(prompt.dismiss());
            }).create();
            prompt.setDelegate(new PromptInstanceDelegate() {
                @Override
                public void onPromptDismiss(BasePrompt p) {   // the page took it back
                    given = true;
                    d.dismiss();
                }
            });
            d.show();
            return result;
        }
    }
}
