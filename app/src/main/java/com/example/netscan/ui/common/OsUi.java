package com.example.netscan.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.netscan.R;
import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.OsInfo;
import com.example.netscan.data.model.OsInfo.Confidence;
import com.example.netscan.data.model.OsInfo.Family;
import com.example.netscan.utils.OsGuesser;

import java.util.ArrayList;
import java.util.List;

// Fichier : ui/common/OsUi.java
// Rôle : affichage du système d'exploitation, partagé par les modes Simple et
// Expert : résolution (cet appareil, passerelle), pastille colorée, icône
// d'appareil et texte détaillé (confiance + indices). La couleur n'est jamais
// seule : le nom du système est toujours écrit (daltonisme).
public final class OsUi {

    private OsUi() {
    }

    // OS à afficher pour un hôte. Deux certitudes que l'analyse réseau ne peut
    // pas avoir : le téléphone qui exécute NetScan est un Android connu, et la
    // passerelle du réseau est une box ou un routeur.
    public static OsInfo resolve(Context context, Host host, String ownIp, String gatewayIp) {
        if (host == null) {
            return OsInfo.unknown();
        }
        String ip = host.getIpAddress();
        if (ip != null && ip.equals(ownIp)) {
            return OsInfo.exact(Family.ANDROID,
                    context.getString(R.string.os_android_format, Build.VERSION.RELEASE),
                    context.getString(R.string.os_this_device_reason));
        }
        OsInfo info = OsGuesser.analyze(host);
        if (ip != null && ip.equals(gatewayIp) && isWeak(info)) {
            List<String> why = new ArrayList<>();
            why.add(context.getString(R.string.os_gateway_reason));
            why.addAll(info.evidence());
            return OsInfo.of(Family.NETWORK, Confidence.MEDIUM, why, false);
        }
        return info;
    }

    // Estimation trop faible ou trop générique pour contredire « c'est la box ».
    private static boolean isWeak(OsInfo info) {
        return !info.isKnown() || info.confidence() == Confidence.LOW
                || info.family() == Family.LINUX || info.family() == Family.MOBILE
                || info.family() == Family.IOT;
    }

    // Pastille « Windows », « Android 15 », « Box / NAS », « Windows ? » (incertain).
    public static void bindChip(TextView chip, OsInfo os) {
        Context context = chip.getContext();
        int[] colors = colorsFor(context, os.family());
        String text = os.shortLabel();
        if (os.isKnown() && os.confidence() == Confidence.LOW) {
            text = context.getString(R.string.os_chip_uncertain_format, text);
        }
        chip.setText(text);
        chip.setTextColor(colors[1]);
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors[0]);
        background.setCornerRadius(
                context.getResources().getDimension(R.dimen.chip_corner_radius));
        chip.setBackground(background);
    }

    // Icône d'appareil dans une pastille ronde de la couleur de la famille.
    public static void bindIcon(ImageView image, View container, OsInfo os) {
        Context context = image.getContext();
        int[] colors = colorsFor(context, os.family());
        image.setImageResource(iconFor(os));
        ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(colors[1]));
        if (container != null) {
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(colors[0]);
            container.setBackground(circle);
        }
    }

    public static int iconFor(OsInfo os) {
        switch (os.family()) {
            case WINDOWS:
                return R.drawable.ic_os_computer;
            case APPLE:
                return os.isMobile() ? R.drawable.ic_os_phone : R.drawable.ic_os_computer;
            case ANDROID:
            case MOBILE:
                return R.drawable.ic_os_phone;
            case TV:
                return R.drawable.ic_os_tv;
            case PRINTER:
                return R.drawable.ic_os_printer;
            case NETWORK:
                return R.drawable.ic_os_router;
            case LINUX:
                return R.drawable.ic_os_server;
            case IOT:
                return R.drawable.ic_os_iot;
            default:
                return R.drawable.ic_os_unknown;
        }
    }

    // {fond, texte}. Couleurs de thème (Windows bleu, Apple violet, Android vert)
    // ou palette os_* : toutes ont une variante nuit.
    static int[] colorsFor(Context context, Family family) {
        int bg;
        int fg;
        switch (family) {
            case WINDOWS:
                bg = R.color.secondary_container;
                fg = R.color.on_secondary_container;
                break;
            case APPLE:
                bg = R.color.tertiary_container;
                fg = R.color.on_tertiary_container;
                break;
            case ANDROID:
                bg = R.color.primary_container;
                fg = R.color.on_primary_container;
                break;
            case LINUX:
                bg = R.color.os_amber_bg;
                fg = R.color.os_amber_fg;
                break;
            case TV:
                bg = R.color.os_teal_bg;
                fg = R.color.os_teal_fg;
                break;
            case PRINTER:
                bg = R.color.os_rose_bg;
                fg = R.color.os_rose_fg;
                break;
            case NETWORK:
            case IOT:
            case MOBILE:
                bg = R.color.os_slate_bg;
                fg = R.color.os_slate_fg;
                break;
            default:
                bg = R.color.surface_variant;
                fg = R.color.on_surface_variant;
                break;
        }
        return new int[]{ContextCompat.getColor(context, bg), ContextCompat.getColor(context, fg)};
    }

    // Texte de la fiche : libellé, confiance, TTL (vue technique) et indices.
    public static String describe(Context context, OsInfo os, int ttl, boolean technical) {
        StringBuilder text = new StringBuilder(os.label());
        if (os.isKnown()) {
            text.append('\n').append(context.getString(R.string.os_confidence_format,
                    os.confidence().description()));
        }
        if (technical) {
            text.append('\n').append(ttl > 0
                    ? context.getString(R.string.os_ttl_format, ttl)
                    : context.getString(R.string.os_ttl_unknown));
        }
        text.append("\n\n").append(context.getString(R.string.os_evidence_title)).append(" :");
        if (os.evidence().isEmpty()) {
            text.append('\n').append(context.getString(R.string.os_evidence_none));
        } else {
            for (String proof : os.evidence()) {
                text.append("\n• ").append(proof);
            }
        }
        return text.toString();
    }
}
