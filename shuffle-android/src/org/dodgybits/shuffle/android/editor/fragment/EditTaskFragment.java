package org.dodgybits.shuffle.android.editor.fragment;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.dodgybits.android.shuffle.R;
import org.dodgybits.shuffle.android.core.model.Id;
import org.dodgybits.shuffle.android.core.model.Task;
import org.dodgybits.shuffle.android.core.model.persistence.TaskPersister;
import org.dodgybits.shuffle.android.core.util.CalendarUtils;
import org.dodgybits.shuffle.android.core.util.OSUtils;
import org.dodgybits.shuffle.android.persistence.provider.ContextProvider;
import org.dodgybits.shuffle.android.persistence.provider.ProjectProvider;
import org.dodgybits.shuffle.android.persistence.provider.TaskProvider;
import org.dodgybits.shuffle.android.preference.model.Preferences;

import java.util.TimeZone;

public class EditTaskFragment extends AbstractEditFragment<Task>
        implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "EditTaskFragment";


    private static final String[] CONTEXT_PROJECTION = new String[] {
            ContextProvider.Contexts._ID,
            ContextProvider.Contexts.NAME
    };

    private static final String[] PROJECT_PROJECTION = new String[] {
            ProjectProvider.Projects._ID,
            ProjectProvider.Projects.NAME
    };

    private static final int NEW_CONTEXT_CODE = 100;
    private static final int NEW_PROJECT_CODE = 101;

    private EditText mDescriptionWidget;
    private Spinner mContextSpinner;
    private Spinner mProjectSpinner;
    private EditText mDetailsWidget;

    private String[] mContextNames;
    private long[] mContextIds;

    private String[] mProjectNames;
    private long[] mProjectIds;

    private boolean mSchedulingExpanded;
    private TextView mShowFromDateButton;
    private TextView mShowFromTimeButton;
    private View mClearShowFromButton;
    private TextView mDueDateButton;
    private TextView mDueTimeButton;
    private View mClearDueButton;
    private CheckBox mAllDayCheckBox;

    private boolean mShowStart;
    private Time mShowFromTime;
    private boolean mShowDue;
    private Time mDueTime;

    private View mSchedulingExtra;
    private TextView mSchedulingDetail;
    private View mExpandButton;
    private View mCollapseButton;

    private View mCompleteEntry;
    private CheckBox mCompletedCheckBox;

    private View mDeletedEntry;
    private View mDeletedDivider;
    private CheckBox mDeletedCheckBox;

    private View mUpdateCalendarEntry;
    private CheckBox mUpdateCalendarCheckBox;
    private TextView mCalendarLabel;
    private TextView mCalendarDetail;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mShowFromTime = new Time();
        mDueTime = new Time();

        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        Log.d(TAG, "Got resultCode " + resultCode + " with data " + data);
        switch (requestCode) {
            case NEW_CONTEXT_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        long newContextId = ContentUris.parseId(data.getData());
                        setupContextSpinner();
                        setSpinnerSelection(mContextSpinner, mContextIds, newContextId);
                    }
                }
                break;
            case NEW_PROJECT_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        long newProjectId = ContentUris.parseId(data.getData());
                        setupProjectSpinner();
                        setSpinnerSelection(mProjectSpinner, mProjectIds, newProjectId);
                    }
                }
                break;
            default:
                Log.e(TAG, "Unknown requestCode: " + requestCode);
        }
    }

    @Override
    protected boolean isValid() {
        String description = mDescriptionWidget.getText().toString();
        return !TextUtils.isEmpty(description);
    }

    @Override
    protected void updateUIFromExtras(Bundle extras) {
        if (extras != null) {
            Long contextId = extras.getLong(TaskProvider.Tasks.CONTEXT_ID);
            setSpinnerSelection(mContextSpinner, mContextIds, contextId);

            Long projectId = extras.getLong(TaskProvider.Tasks.PROJECT_ID);
            setSpinnerSelection(mProjectSpinner, mProjectIds, projectId);
        }

        mCompleteEntry.setVisibility(View.GONE);
        mDeletedEntry.setVisibility(View.GONE);
        mDeletedDivider.setVisibility(View.GONE);
        mDeletedCheckBox.setChecked(false);

        setWhenDefaults();
        populateWhen();

        setSchedulingVisibility(false);

        mShowFromTimeButton.setVisibility(View.VISIBLE);
        mDueTimeButton.setVisibility(View.VISIBLE);
        updateCalendarPanel();
    }

    @Override
    protected void updateUIFromItem(Task task) {
        // If we hadn't previously retrieved the original task, do so
        // now.  This allows the user to revert their changes.
        if (mOriginalItem == null) {
            mOriginalItem = task;
        }

        mCompleteEntry.setVisibility(View.VISIBLE);

        final String details = task.getDetails();
        mDetailsWidget.setTextKeepState(details == null ? "" : details);

        mDescriptionWidget.setTextKeepState(task.getDescription());

        final Id contextId = task.getContextId();
        if (contextId.isInitialised()) {
            setSpinnerSelection(mContextSpinner, mContextIds, contextId.getId());
        }

        final Id projectId = task.getProjectId();
        if (projectId.isInitialised()) {
            setSpinnerSelection(mProjectSpinner, mProjectIds, projectId.getId());
        }

        boolean allDay = task.isAllDay();
        if (allDay) {
            String tz = mShowFromTime.timezone;
            mShowFromTime.timezone = Time.TIMEZONE_UTC;
            mShowFromTime.set(task.getStartDate());
            mShowFromTime.timezone = tz;

            // Calling normalize to calculate isDst
            mShowFromTime.normalize(true);
        } else {
            mShowFromTime.set(task.getStartDate());
        }

        if (allDay) {
            String tz = mShowFromTime.timezone;
            mDueTime.timezone = Time.TIMEZONE_UTC;
            mDueTime.set(task.getDueDate());
            mDueTime.timezone = tz;

            // Calling normalize to calculate isDst
            mDueTime.normalize(true);
        } else {
            mDueTime.set(task.getDueDate());
        }


        setWhenDefaults();
        populateWhen();

        // show scheduling section if either start or due date are set
        mSchedulingExpanded = mShowStart || mShowDue;
        setSchedulingVisibility(mSchedulingExpanded);

        mAllDayCheckBox.setChecked(allDay);
        updateTimeVisibility(!allDay);

        mCompletedCheckBox.setChecked(task.isComplete());

        mDeletedEntry.setVisibility(task.isDeleted() ? View.VISIBLE : View.GONE);
        mDeletedDivider.setVisibility(task.isDeleted() ? View.VISIBLE : View.GONE);
        mDeletedCheckBox.setChecked(task.isDeleted());

        updateCalendarPanel();
    }

    @Override
    protected Task createItemFromUI(boolean commitValues) {
        Task.Builder builder = Task.newBuilder();
        if (mOriginalItem != null) {
            builder.mergeFrom(mOriginalItem);
        }

        final String description = mDescriptionWidget.getText().toString();
        final long modified = System.currentTimeMillis();
        final String details = mDetailsWidget.getText().toString();
        final Id contextId = getSpinnerSelectedId(mContextSpinner, mContextIds);
        final Id projectId = getSpinnerSelectedId(mProjectSpinner, mProjectIds);
        final boolean allDay = mAllDayCheckBox.isChecked();
        final boolean complete = mCompletedCheckBox.isChecked();
        final boolean deleted = mDeletedCheckBox.isChecked();
        final boolean active = true;

        builder
                .setDescription(description)
                .setModifiedDate(modified)
                .setDetails(details)
                .setContextId(contextId)
                .setProjectId(projectId)
                .setAllDay(allDay)
                .setComplete(complete)
                .setDeleted(deleted)
                .setActive(active);

        // If we are creating a new task, set the creation date
        if (mIsNewEntity) {
            builder.setCreatedDate(modified);
        }

        String timezone;
        long startMillis = 0L;
        long dueMillis = 0L;

        if (allDay) {
            // Reset start and end time, increment the monthDay by 1, and set
            // the timezone to UTC, as required for all-day events.
            timezone = Time.TIMEZONE_UTC;
            mShowFromTime.hour = 0;
            mShowFromTime.minute = 0;
            mShowFromTime.second = 0;
            mShowFromTime.timezone = timezone;
            startMillis = mShowFromTime.normalize(true);

            mDueTime.hour = 0;
            mDueTime.minute = 0;
            mDueTime.second = 0;
            mDueTime.monthDay++;
            mDueTime.timezone = timezone;
            dueMillis = mDueTime.normalize(true);
        } else {
            if (mShowStart && !Time.isEpoch(mShowFromTime)) {
                startMillis = mShowFromTime.toMillis(true);
            }

            if (mShowDue && !Time.isEpoch(mDueTime)) {
                dueMillis = mDueTime.toMillis(true);
            }

            if (mIsNewEntity) {
                // The timezone for a new task is the currently displayed timezone
                timezone = TimeZone.getDefault().getID();
            }
            else
            {
                timezone = mOriginalItem.getTimezone();

                // The timezone might be null if we are changing an existing
                // all-day task to a non-all-day event.  We need to assign
                // a timezone to the non-all-day task.
                if (TextUtils.isEmpty(timezone)) {
                    timezone = TimeZone.getDefault().getID();
                }
            }
        }

        final int order;
        if (commitValues) {
            order = ((TaskPersister)mPersister).calculateTaskOrder(mOriginalItem, projectId, dueMillis);
        } else if (mOriginalItem == null) {
            order = 0;
        } else {
            order = mOriginalItem.getOrder();
        }

        builder
                .setTimezone(timezone)
                .setStartDate(startMillis)
                .setDueDate(dueMillis)
                .setOrder(order);


        Id eventId = mOriginalItem == null ? Id.NONE : mOriginalItem.getCalendarEventId();
        final boolean updateCalendar = mUpdateCalendarCheckBox.isChecked();

        if (updateCalendar) {
            Uri calEntryUri = addOrUpdateCalendarEvent(
                    eventId, description, details,
                    projectId, contextId, timezone, startMillis,
                    dueMillis, allDay);
            if (calEntryUri != null) {
                eventId = Id.create(ContentUris.parseId(calEntryUri));
                mNextIntent = new Intent(Intent.ACTION_EDIT, calEntryUri);
                mNextIntent.putExtra("beginTime", startMillis);
                mNextIntent.putExtra("endTime", dueMillis);
            }
            Log.i(TAG, "Updated calendar event " + eventId);
        }
        builder.setCalendarEventId(eventId);

        return builder.build();
    }

    private Uri addOrUpdateCalendarEvent(
            Id calEventId, String title, String description,
            Id projectId, Id contextId,
            String timezone, long start, long end, boolean allDay) {
        if (projectId.isInitialised()) {
            String projectName = getProjectName(projectId);
            title = projectName + " - " + title;
        }
        if (description == null) {
            description = "";
        }

        ContentValues values = new ContentValues();
        if (!TextUtils.isEmpty(timezone)) {
            values.put("eventTimezone", timezone);
        }
        values.put("calendar_id", Preferences.getCalendarId(getActivity()));
        values.put("title", title);
        values.put("allDay", allDay ? 1 : 0);
        if (start > 0L) {
            values.put("dtstart", start); // long (start date in ms)
        }
        if (end > 0L) {
            values.put("dtend", end);     // long (end date in ms)
        }
        values.put("description", description);
        values.put("hasAlarm", 0);

        if (!OSUtils.atLeastICS()) {
            values.put("transparency", 0);
            values.put("visibility", 0);
        }

        if (contextId.isInitialised()) {
            String contextName = getContextName(contextId);
            values.put("eventLocation", contextName);
        }

        Uri eventUri = null;
        try {
            eventUri = addCalendarEntry(values, calEventId, CalendarUtils.getEventContentUri());
        } catch (Exception e) {
            Log.e(TAG, "Attempt failed to create calendar entry", e);
        }

        return eventUri;
    }

    private Uri addCalendarEntry(ContentValues values, Id oldId, Uri baseUri) {
        ContentResolver cr = getActivity().getContentResolver();
        int updateCount = 0;
        Uri eventUri = null;
        if (oldId.isInitialised()) {
            eventUri = ContentUris.appendId(baseUri.buildUpon(), oldId.getId()).build();
            // it's possible the old event was deleted, check number of records updated
            updateCount = cr.update(eventUri, values, null, null);
        }
        if (updateCount == 0) {
            eventUri = cr.insert(baseUri, values);
        }
        return eventUri;
    }

    /**
     * @return id of layout for this view
     */
    @Override
    protected int getContentViewResId() {
        return R.layout.task_editor;
    }

    @Override
    protected CharSequence getItemName() {
        return getString(R.string.task_name);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.context_add: {
                Intent addContextIntent = new Intent(Intent.ACTION_INSERT, ContextProvider.Contexts.CONTENT_URI);
                startActivityForResult(addContextIntent, NEW_CONTEXT_CODE);
                break;
            }

            case R.id.project_add: {
                Intent addProjectIntent = new Intent(Intent.ACTION_INSERT, ProjectProvider.Projects.CONTENT_URI);
                startActivityForResult(addProjectIntent, NEW_PROJECT_CODE);
                break;
            }

            case R.id.scheduling_entry: {
                toggleSchedulingSection();
                break;
            }

            case R.id.completed_entry: {
                mCompletedCheckBox.toggle();
                break;
            }

            case R.id.deleted_entry: {
                mDeletedCheckBox.toggle();
                break;
            }

            case R.id.gcal_entry: {
                CheckBox checkBox = (CheckBox) v.findViewById(R.id.update_calendar_checkbox);
                checkBox.toggle();
                break;
            }

            case R.id.clear_from_date: {
                mAllDayCheckBox.setChecked(false);
                mShowFromTime = new Time();
                setWhenDefaults();
                populateWhen();
                updateCalendarPanel();
                break;
            }

            case R.id.clear_due_date: {
                mAllDayCheckBox.setChecked(false);
                mDueTime = new Time();
                setWhenDefaults();
                populateWhen();
                updateCalendarPanel();
                break;
            }

            default:
                super.onClick(v);
                break;
        }
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            if (mDueTime.hour == 0 && mDueTime.minute == 0) {
                mDueTime.monthDay--;

                // Do not allow an event to have an end time before the start time.
                if (mDueTime.before(mShowFromTime)) {
                    mDueTime.set(mShowFromTime);
                }
            }
        } else {
            if (mDueTime.hour == 0 && mDueTime.minute == 0) {
                mDueTime.monthDay++;
            }
        }

        mShowStart = true;
        long startMillis = mShowFromTime.normalize(true);
        setDate(mShowFromDateButton, startMillis, mShowStart);
        setTime(mShowFromTimeButton, startMillis, mShowStart);

        mShowDue = true;
        long dueMillis = mDueTime.normalize(true);
        setDate(mDueDateButton, dueMillis, mShowDue);
        setTime(mDueTimeButton, dueMillis, mShowDue);

        updateTimeVisibility(!isChecked);
    }

    private void updateTimeVisibility(boolean showTime) {
        if (showTime) {
            mShowFromTimeButton.setVisibility(View.VISIBLE);
            mDueTimeButton.setVisibility(View.VISIBLE);
        } else {
            mShowFromTimeButton.setVisibility(View.GONE);
            mDueTimeButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void loadCursor() {
        // Get the task if we're editing
        if (mUri != null && !mIsNewEntity)
        {
            mCursor = getActivity().managedQuery(mUri, TaskProvider.Tasks.FULL_PROJECTION, null, null, null);
            if (mCursor == null || mCursor.getCount() == 0) {
                // The cursor is empty. This can happen if the event was deleted.
                getActivity().finish();
            }
            mCursor.moveToFirst();
        }
    }

    @Override
    protected void findViewsAndAddListeners() {
        mDescriptionWidget = (EditText) getView().findViewById(R.id.description);
        mContextSpinner = (Spinner) getView().findViewById(R.id.context);
        mProjectSpinner = (Spinner) getView().findViewById(R.id.project);
        mDetailsWidget = (EditText) getView().findViewById(R.id.details);

        mShowFromDateButton = (TextView) getView().findViewById(R.id.show_from_date);
        mShowFromTimeButton = (TextView) getView().findViewById(R.id.show_from_time);
        mClearShowFromButton = getView().findViewById(R.id.clear_from_date);
        mDueDateButton = (TextView) getView().findViewById(R.id.due_date);
        mDueTimeButton = (TextView) getView().findViewById(R.id.due_time);
        mClearDueButton = getView().findViewById(R.id.clear_due_date);
        mAllDayCheckBox = (CheckBox) getView().findViewById(R.id.is_all_day);

        mCompleteEntry = getView().findViewById(R.id.completed_entry);
        mDeletedEntry = getView().findViewById(R.id.deleted_entry);
        mDeletedDivider = getView().findViewById(R.id.deleted_divider);
        mDeletedCheckBox = (CheckBox) getView().findViewById(R.id.deleted_entry_checkbox);
        mUpdateCalendarEntry = getView().findViewById(R.id.gcal_entry);

        setupContextSpinner();
        View addContextButton = getView().findViewById(R.id.context_add);
        addContextButton.setOnClickListener(this);
        addContextButton.setOnFocusChangeListener(this);

        setupProjectSpinner();
        View addProjectButton = getView().findViewById(R.id.project_add);
        addProjectButton.setOnClickListener(this);
        addProjectButton.setOnFocusChangeListener(this);

        mCompleteEntry.setOnClickListener(this);
        mCompleteEntry.setOnFocusChangeListener(this);
        mCompletedCheckBox = (CheckBox) mCompleteEntry.findViewById(R.id.completed_entry_checkbox);

        mDeletedEntry.setOnClickListener(this);
        mDeletedEntry.setOnFocusChangeListener(this);

        mUpdateCalendarEntry.setOnClickListener(this);
        mUpdateCalendarEntry.setOnFocusChangeListener(this);
        mUpdateCalendarCheckBox = (CheckBox) mUpdateCalendarEntry.findViewById(R.id.update_calendar_checkbox);
        mCalendarLabel = (TextView) mUpdateCalendarEntry.findViewById(R.id.gcal_label);
        mCalendarDetail = (TextView) mUpdateCalendarEntry.findViewById(R.id.gcal_detail);

        mShowFromDateButton.setOnClickListener(new DateClickListener(mShowFromTime));
        mShowFromTimeButton.setOnClickListener(new TimeClickListener(mShowFromTime));
        mClearShowFromButton.setOnClickListener(this);

        mDueDateButton.setOnClickListener(new DateClickListener(mDueTime));
        mDueTimeButton.setOnClickListener(new TimeClickListener(mDueTime));
        mClearDueButton.setOnClickListener(this);

        mAllDayCheckBox.setOnCheckedChangeListener(this);

        ViewGroup schedulingSection = (ViewGroup) getView().findViewById(R.id.scheduling_section);
        View schedulingEntry = getView().findViewById(R.id.scheduling_entry);
        schedulingEntry.setOnClickListener(this);
        schedulingEntry.setOnFocusChangeListener(this);

        mSchedulingExtra = schedulingSection.findViewById(R.id.scheduling_extra);
        mExpandButton = schedulingEntry.findViewById(R.id.expand);
        mCollapseButton = schedulingEntry.findViewById(R.id.collapse);
        mSchedulingDetail = (TextView) schedulingEntry.findViewById(R.id.scheduling_detail);
        mSchedulingExpanded = mSchedulingExtra.getVisibility() == View.VISIBLE;

    }

    private void setupContextSpinner() {
        Cursor contextCursor = getActivity().getContentResolver().query(
                ContextProvider.Contexts.CONTENT_URI, CONTEXT_PROJECTION,
                ContextProvider.Contexts.DELETED + "=0", null, ContextProvider.Contexts.NAME + " ASC");
        int arraySize = contextCursor.getCount() + 1;
        mContextIds = new long[arraySize];
        mContextIds[0] = 0;
        mContextNames = new String[arraySize];
        mContextNames[0] = getText(R.string.none_empty).toString();
        for (int i = 1; i < arraySize; i++) {
            contextCursor.moveToNext();
            mContextIds[i] = contextCursor.getLong(0);
            mContextNames[i] = contextCursor.getString(1);
        }
        contextCursor.close();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                getActivity(), android.R.layout.simple_list_item_1, mContextNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mContextSpinner.setAdapter(adapter);
    }

    private void setupProjectSpinner() {
        Cursor projectCursor = getActivity().getContentResolver().query(
                ProjectProvider.Projects.CONTENT_URI, PROJECT_PROJECTION,
                ProjectProvider.Projects.DELETED + " = 0", null, ProjectProvider.Projects.NAME + " ASC");
        int arraySize = projectCursor.getCount() + 1;
        mProjectIds = new long[arraySize];
        mProjectIds[0] = 0;
        mProjectNames = new String[arraySize];
        mProjectNames[0] = getText(R.string.none_empty).toString();
        for (int i = 1; i < arraySize; i++) {
            projectCursor.moveToNext();
            mProjectIds[i] = projectCursor.getLong(0);
            mProjectNames[i] = projectCursor.getString(1);
        }
        projectCursor.close();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                getActivity(), android.R.layout.simple_list_item_1, mProjectNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mProjectSpinner.setAdapter(adapter);
    }

    private Id getSpinnerSelectedId(Spinner spinner, long[] ids) {
        Id id = Id.NONE;
        int selectedItemPosition = spinner.getSelectedItemPosition();
        if (selectedItemPosition > 0) {
            id = Id.create(ids[selectedItemPosition]);
        }
        return id;
    }

    private void setSpinnerSelection(Spinner spinner, long[] ids, Long id) {
        if (id == null || id == 0) {
            spinner.setSelection(0);
        } else {
            for (int i = 1; i < ids.length; i++) {
                if (ids[i] == id) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }
    }

    private String getContextName(Id contextId) {
        String name = "";
        final long id = contextId.getId();
        for(int i = 0; i < mContextIds.length; i++) {
            long currentId = mContextIds[i];
            if (currentId == id) {
                name = mContextNames[i];
                break;
            }
        }
        return name;
    }

    private String getProjectName(Id projectId) {
        String name = "";
        final long id = projectId.getId();
        for(int i = 0; i < mProjectIds.length; i++) {
            long currentId = mProjectIds[i];
            if (currentId == id) {
                name = mProjectNames[i];
                break;
            }
        }
        return name;
    }

    private void toggleSchedulingSection() {
        mSchedulingExpanded = !mSchedulingExpanded;
        setSchedulingVisibility(mSchedulingExpanded);
    }

    private void setSchedulingVisibility(boolean visible) {
        if (visible) {
            mSchedulingExtra.setVisibility(View.VISIBLE);
            mExpandButton.setVisibility(View.GONE);
            mCollapseButton.setVisibility(View.VISIBLE);
            mSchedulingDetail.setText(R.string.scheduling_expanded);
        } else {
            mSchedulingExtra.setVisibility(View.GONE);
            mExpandButton.setVisibility(View.VISIBLE);
            mCollapseButton.setVisibility(View.GONE);
            mSchedulingDetail.setText(R.string.scheduling_collapsed);
        }
    }

    private void setWhenDefaults() {
        // it's possible to have:
        // 1) no times set
        // 2) due time set, but not show from time
        // 3) show from and due time set

        mShowStart = !Time.isEpoch(mShowFromTime);
        mShowDue = !Time.isEpoch(mDueTime);

        if (!mShowStart && !mShowDue) {
            mShowFromTime.setToNow();

            // Round the time to the nearest half hour.
            mShowFromTime.second = 0;
            int minute = mShowFromTime.minute;
            if (minute > 0 && minute <= 30) {
                mShowFromTime.minute = 30;
            } else {
                mShowFromTime.minute = 0;
                mShowFromTime.hour += 1;
            }

            long startMillis = mShowFromTime.normalize(true /* ignore isDst */);
            mDueTime.set(startMillis + DateUtils.HOUR_IN_MILLIS);
        } else if (!mShowStart) {
            // default start to same as due
            mShowFromTime.set(mDueTime);
        }
    }

    private void populateWhen() {
        long startMillis = mShowFromTime.toMillis(false /* use isDst */);
        long endMillis = mDueTime.toMillis(false /* use isDst */);
        setDate(mShowFromDateButton, startMillis, mShowStart);
        setDate(mDueDateButton, endMillis, mShowDue);

        setTime(mShowFromTimeButton, startMillis, mShowStart);
        setTime(mDueTimeButton, endMillis, mShowDue);
    }

    private void setDate(TextView view, long millis, boolean showValue) {
        CharSequence value;
        if (showValue) {
            int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR |
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_MONTH |
                    DateUtils.FORMAT_ABBREV_WEEKDAY;
            value = DateUtils.formatDateTime(getActivity(), millis, flags);
        } else {
            value = "";
        }
        view.setText(value);
    }

    private void setTime(TextView view, long millis, boolean showValue) {
        CharSequence value;
        if (showValue) {
            int flags = DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(getActivity())) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            value = DateUtils.formatDateTime(getActivity(), millis, flags);
        } else {
            value = "";
        }
        view.setText(value);
    }


    private void updateCalendarPanel() {
        boolean enabled = true;
        if (mOriginalItem != null &&
                mOriginalItem.getCalendarEventId().isInitialised()) {
            mCalendarLabel.setText(getString(R.string.update_gcal_title));
            mCalendarDetail.setText(getString(R.string.update_gcal_detail));
        } else if (mShowDue && mShowStart) {
            mCalendarLabel.setText(getString(R.string.add_to_gcal_title));
            mCalendarDetail.setText(getString(R.string.add_to_gcal_detail));
        } else {
            mCalendarLabel.setText(getString(R.string.add_to_gcal_title));
            mCalendarDetail.setText(getString(R.string.add_to_gcal_detail_disabled));
            enabled = false;
        }
        mUpdateCalendarEntry.setEnabled(enabled);
        mUpdateCalendarCheckBox.setEnabled(enabled);
    }

    /* This class is used to update the time buttons. */
    private class TimeListener implements TimePickerDialog.OnTimeSetListener {
        private View mView;

        public TimeListener(View view) {
            mView = view;
        }

        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            // Cache the member variables locally to avoid inner class overhead.
            Time startTime = mShowFromTime;
            Time dueTime = mDueTime;

            // Cache the start and due millis so that we limit the number
            // of calls to normalize() and toMillis(), which are fairly
            // expensive.
            long startMillis;
            long dueMillis;
            if (mView == mShowFromTimeButton) {
                // The start time was changed.
                int hourDuration = dueTime.hour - startTime.hour;
                int minuteDuration = dueTime.minute - startTime.minute;

                startTime.hour = hourOfDay;
                startTime.minute = minute;
                startMillis = startTime.normalize(true);
                mShowStart = true;

                // Also update the due time to keep the duration constant.
                dueTime.hour = hourOfDay + hourDuration;
                dueTime.minute = minute + minuteDuration;
                dueMillis = dueTime.normalize(true);
                mShowDue = true;
            } else {
                // The due time was changed.
                startMillis = startTime.toMillis(true);
                dueTime.hour = hourOfDay;
                dueTime.minute = minute;
                dueMillis = dueTime.normalize(true);
                mShowDue = true;

                if (mShowStart) {
                    // Do not allow an event to have a due time before the start time.
                    if (dueTime.before(startTime)) {
                        dueTime.set(startTime);
                        dueMillis = startMillis;
                    }
                } else {
                    // if start time is not shown, default it to be the same as due time
                    startTime.set(dueTime);
                    mShowStart = true;
                }
            }

            // update all 4 buttons in case visibility has changed
            setDate(mShowFromDateButton, startMillis, mShowStart);
            setTime(mShowFromTimeButton, startMillis, mShowStart);
            setDate(mDueDateButton, dueMillis, mShowDue);
            setTime(mDueTimeButton, dueMillis, mShowDue);
            updateCalendarPanel();
        }

    }

    private class TimeClickListener implements View.OnClickListener {
        private Time mTime;

        public TimeClickListener(Time time) {
            mTime = time;
        }

        public void onClick(View v) {
            new TimePickerDialog(getActivity(), new TimeListener(v),
                    mTime.hour, mTime.minute,
                    DateFormat.is24HourFormat(getActivity())).show();
        }
    }

    private class DateListener implements DatePickerDialog.OnDateSetListener {
        View mView;

        public DateListener(View view) {
            mView = view;
        }

        public void onDateSet(DatePicker view, int year, int month, int monthDay) {
            // Cache the member variables locally to avoid inner class overhead.
            Time startTime = mShowFromTime;
            Time dueTime = mDueTime;

            // Cache the start and due millis so that we limit the number
            // of calls to normalize() and toMillis(), which are fairly
            // expensive.
            long startMillis;
            long dueMillis;
            if (mView == mShowFromDateButton) {
                // The start date was changed.
                int yearDuration = dueTime.year - startTime.year;
                int monthDuration = dueTime.month - startTime.month;
                int monthDayDuration = dueTime.monthDay - startTime.monthDay;

                startTime.year = year;
                startTime.month = month;
                startTime.monthDay = monthDay;
                startMillis = startTime.normalize(true);
                mShowStart = true;

                // Also update the end date to keep the duration constant.
                dueTime.year = year + yearDuration;
                dueTime.month = month + monthDuration;
                dueTime.monthDay = monthDay + monthDayDuration;
                dueMillis = dueTime.normalize(true);
                mShowDue = true;
            } else {
                // The end date was changed.
                startMillis = startTime.toMillis(true);
                dueTime.year = year;
                dueTime.month = month;
                dueTime.monthDay = monthDay;
                dueMillis = dueTime.normalize(true);
                mShowDue = true;

                if (mShowStart) {
                    // Do not allow an event to have an end time before the start time.
                    if (dueTime.before(startTime)) {
                        dueTime.set(startTime);
                        dueMillis = startMillis;
                    }
                } else {
                    // if start time is not shown, default it to be the same as due time
                    startTime.set(dueTime);
                    mShowStart = true;
                }
            }

            // update all 4 buttons in case visibility has changed
            setDate(mShowFromDateButton, startMillis, mShowStart);
            setTime(mShowFromTimeButton, startMillis, mShowStart);
            setDate(mDueDateButton, dueMillis, mShowDue);
            setTime(mDueTimeButton, dueMillis, mShowDue);
            updateCalendarPanel();
        }

    }

    private class DateClickListener implements View.OnClickListener {
        private Time mTime;

        public DateClickListener(Time time) {
            mTime = time;
        }

        public void onClick(View v) {
            new DatePickerDialog(getActivity(), new DateListener(v), mTime.year,
                    mTime.month, mTime.monthDay).show();
        }
    }
    

}