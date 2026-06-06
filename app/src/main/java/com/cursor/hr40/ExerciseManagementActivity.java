package com.cursor.hr40;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ExerciseManagementActivity extends AppCompatActivity {
    private final List<String> exerciseNames = new ArrayList<>();
    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = PageScaffold.open(this, "动作管理");

        TextInputLayout addLayout = new TextInputLayout(this);
        addLayout.setHint("新动作名称");
        TextInputEditText addInput = new TextInputEditText(this);
        addLayout.addView(addInput);
        root.addView(addLayout, PageScaffold.matchWrap());

        MaterialButton addButton = PageScaffold.actionButton(this, "添加动作", () -> {
            String name = addInput.getText() == null ? "" : addInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "请输入动作名称", Toast.LENGTH_SHORT).show();
                return;
            }
            OnlineFeatures.addExercise(this, name, () -> {
                addInput.setText("");
                reloadList();
            }, () -> { });
        });
        root.addView(addButton, PageScaffold.matchWrap());

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer, PageScaffold.matchWrap());

        reloadList();
    }

    private void reloadList() {
        listContainer.removeAllViews();
        exerciseNames.clear();
        try {
            exerciseNames.addAll(ExerciseStore.loadExercises(this));
        } catch (IOException ignored) {
        }
        if (exerciseNames.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无动作，请先添加。");
            empty.setTextColor(Color.DKGRAY);
            PageScaffold.styleBody(this, empty);
            listContainer.addView(empty, PageScaffold.matchWrap());
            return;
        }
        for (String name : new ArrayList<>(exerciseNames)) {
            MaterialCardView rowCard = new MaterialCardView(this);
            rowCard.setCardElevation(0f);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView nameView = new TextView(this);
            nameView.setText(name);
            PageScaffold.styleBody(this, nameView);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nameView.setLayoutParams(nameParams);
            MaterialButton deleteButton = new MaterialButton(
                    this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            deleteButton.setText("删除");
            PageScaffold.styleButton(deleteButton);
            deleteButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle("删除动作")
                    .setMessage("确定删除 \"" + name + "\" 吗？")
                    .setPositiveButton("删除", (d, w) -> OnlineFeatures.deleteExercise(
                            this, name, this::reloadList, () -> { }))
                    .setNegativeButton("取消", null)
                    .show());
            row.addView(nameView);
            row.addView(deleteButton);
            rowCard.addView(row);
            listContainer.addView(rowCard, PageScaffold.matchWrap());
        }
    }
}
