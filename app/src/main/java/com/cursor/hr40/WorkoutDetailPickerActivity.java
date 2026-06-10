package com.cursor.hr40;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.List;

public final class WorkoutDetailPickerActivity extends AppCompatActivity {
    private LinearLayout listContainer;
    private Calendar selectedDate;
    private TextInputEditText dateInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = PageScaffold.open(this, "查看运动明细");

        if (OnlineFeatures.workoutDetailShowsDateFilter()) {
            selectedDate = Calendar.getInstance();
            dateInput = PageScaffold.dateFilterField(
                    this,
                    root,
                    "选择日期",
                    WorkoutSessionLabels.formatDateLabel(selectedDate),
                    this::showDatePicker);
        }

        listContainer = PageScaffold.contentArea(this, root);
        OnlineFeatures.refreshWorkoutList(this, this::populateList);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从详情页上传后返回时，重新刷新列表以反映最新上传状态（变绿）
        if (listContainer != null) {
            populateList();
        }
    }

    private void showDatePicker() {
        long selection = selectedDate == null
                ? MaterialDatePicker.todayInUtcMilliseconds()
                : selectedDate.getTimeInMillis();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择日期")
                .setSelection(selection)
                .build();
        picker.addOnPositiveButtonClickListener(value -> {
            if (selectedDate == null) {
                selectedDate = Calendar.getInstance();
            }
            selectedDate.setTimeInMillis(value);
            if (dateInput != null) {
                dateInput.setText(WorkoutSessionLabels.formatDateLabel(selectedDate));
            }
            populateList();
        });
        picker.show(getSupportFragmentManager(), "workout_date_picker");
    }

    private void populateList() {
        List<WorkoutSession> sessions = WorkoutSessionLabels.collectExportableSessions(this);
        if (OnlineFeatures.workoutDetailShowsDateFilter() && selectedDate != null) {
            sessions = WorkoutSessionLabels.filterSessionsOnDate(sessions, selectedDate);
        }

        listContainer.removeAllViews();
        listContainer.setGravity(Gravity.TOP);
        listContainer.setMinimumHeight(0);

        if (sessions.isEmpty()) {
            PageScaffold.showCenteredEmpty(this, listContainer, "暂无数据");
            return;
        }

        for (WorkoutSession session : sessions) {
            MaterialButton itemButton = PageScaffold.actionButton(
                    this,
                    WorkoutSessionLabels.formatSessionLabel(session),
                    () -> {
                        Intent intent = new Intent(this, WorkoutDetailActivity.class);
                        intent.putExtra(WorkoutDetailActivity.EXTRA_SESSION_ID, session.id);
                        startActivity(intent);
                    });
            OnlineFeatures.styleWorkoutListItem(this, itemButton, session);
            listContainer.addView(itemButton, PageScaffold.matchWrap());
        }
    }
}
