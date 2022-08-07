package edu.neu.madcourse.wellness_studio.lightExercises;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import java.util.concurrent.TimeUnit;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.kofigyan.stateprogressbar.StateProgressBar;

import java.sql.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import edu.neu.madcourse.wellness_studio.R;
import edu.neu.madcourse.wellness_studio.utils.UserService;
import edu.neu.madcourse.wellness_studio.utils.Utils;
import localDatabase.AppDatabase;
import localDatabase.enums.ExerciseSet;
import localDatabase.enums.ExerciseStatus;
import localDatabase.lightExercise.LightExercise;
import localDatabase.userInfo.User;
import localDatabase.userInfo.UserDao;

public class LightExercises extends AppCompatActivity implements View.OnClickListener{
    private String TAG = "LightExercises";
    AppDatabase db;
    int hour, min = -1;
    int currentStep = 0;

    TextView timeTextView;
    Switch reminderSwitch;
    ImageView armImageView;
    ImageView backImageView;
    ImageView legImageView;
    HorizontalScrollView scrollViewForFocusedArea;
    HashMap<Integer, Boolean> stepProgressCompletion = new HashMap<Integer, Boolean>();

    ImageView setting_reminder_lightExercises;

    StateProgressBar stateProgressBar;
    PendingIntent pendingIntent;
    AlarmManager alarmManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_light_exercises);
        createNotificationChannel();

        db = AppDatabase.getDbInstance(this.getApplicationContext());

        timeTextView = findViewById(R.id.timeTextView);
        reminderSwitch = findViewById(R.id.reminderSwitch);
        setting_reminder_lightExercises = findViewById(R.id.setting_reminder_lightExercises);
        armImageView = findViewById(R.id.arm);
        backImageView = findViewById(R.id.back);
        legImageView = findViewById(R.id.leg);
        scrollViewForFocusedArea = findViewById(R.id.horizontalScrollViewToChooseFocusArea);
        stateProgressBar = findViewById(R.id.light_exercises_state_progress_bar);

        //set onclick listener for choosing focus area and leads to the actual workout page
        armImageView.setOnClickListener(this);
        backImageView.setOnClickListener(this);
        legImageView.setOnClickListener(this);
        setting_reminder_lightExercises.setOnClickListener(this);
        setReminderSwitch(reminderSwitch);

        // connect to db
        LightExercise lightExercise = UserService.getCurrentLightExercise(db);
        ExerciseSet currentSet = lightExercise.currentSet;
        String exerciseAlarm = UserService.getExerciseReminderAlarm(db);
        Boolean exerciseAlarmOn = UserService.getExerciseAlarmOn(db);

        if (lightExercise == null) {
            Log.d("myApp", "db is null");
            UserService.createNewLightExercise(db);
        }
        //set existing progress to light exercise activity
        else {
            if(currentSet != null) {
                // Scrolling to the focused set, can't be on main thread, won't work
                scrollViewForFocusedArea.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("myApp", "savedCurrentSet" + currentSet);
                        scrollToCurrentSet(currentSet);
                    }
                });
            }
            if(!lightExercise.getExerciseStatus().equals(ExerciseStatus.NOT_STARTED)) {
                //load progress bar with completed steps
                getStepProgressCompletion(lightExercise.getStepOneCompleted(), lightExercise.getStepTwoCompleted(), lightExercise.getStepThreeCompleted(),lightExercise.getStepFourCompleted());
                if(stepProgressCompletion!= null) {
                    setProgressBarProgress();
                }
            }
            if(exerciseAlarmOn != null) {
                reminderSwitch.setChecked(exerciseAlarmOn);
            }
            if(!exerciseAlarm.equals("--:--") && exerciseAlarmOn) {
                convertMiliSecondsToHourAndMin(Long.parseLong(exerciseAlarm));
                String time  = String.format(Locale.getDefault(),"%02d:%02d",hour,min);
                timeTextView.setText(time);
                setAlarm();
            }
        }

    }

    private void scrollToCurrentSet(ExerciseSet currentSet) {
        if(currentSet.equals(ExerciseSet.ARM)) {
            scrollViewForFocusedArea.scrollTo(0,0);
        }
        if(currentSet.equals(ExerciseSet.BACK)) {
            scrollViewForFocusedArea.scrollTo(900,0);
        }
        if(currentSet.equals(ExerciseSet.LEG)) {
            scrollViewForFocusedArea.scrollTo(1600,0);
        }
    }

    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "lightExerciseAndroidChannel";
            String description = "Channel for light exercise alarm";
            int important = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel notificationChannel = new NotificationChannel("lightExerciseAndroid",name,important);
            notificationChannel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }


    //timePicker
    public void popTimePicker(View view) {
        TimePickerDialog.OnTimeSetListener onTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                hour = hourOfDay;
                min = minute;
                String time  = String.format(Locale.getDefault(),"%02d:%02d",hour,min);
                timeTextView.setText(time);
                //save Time to db
                UserService.updateExerciseReminder(db,String.valueOf(convertHourAndMinToMilliSeconds(hour,min)));
            }
        };

        int style = AlertDialog.THEME_HOLO_LIGHT;

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,style,onTimeSetListener,hour,min,true);
        timePickerDialog.setTitle("Select Time");
        timePickerDialog.show();
    }

    //set reminder switch
    public void setReminderSwitch(Switch reminderSwitch) {
        reminderSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    //when alarm hasn't been set and user turns the switch to ON
                    if(hour == -1) {
                        Utils.postToast("please set an alarm!", getApplicationContext());
                        return;
                    }
                    setAlarm();
                }
                else {
                    cancelAlarm();
                }
                UserService.updateExerciseReminderOn(db,isChecked);
                Log.d(TAG,"hour: " + hour);
                Log.d(TAG,"hour: " + min);
            }
        });
    }

    // turns on the alarm, a slight couple mins delay between setted time and actual notification time is expected.
    public void setAlarm() {
        Intent intent = new Intent(this,AlarmReceiver.class);
        int currentHour = Calendar.getInstance().getTime().getHours();
        int currentMin = Calendar.getInstance().getTime().getMinutes();

        long timeAlarmTriggeredInmillis = convertHourAndMinToMilliSeconds(hour,min) - convertHourAndMinToMilliSeconds(currentHour,currentMin);
        //if alarm triggered time to current moment is negative, plus 24 hr and trigger it the tomorrow at this time
        if(timeAlarmTriggeredInmillis < 0) {
            timeAlarmTriggeredInmillis = timeAlarmTriggeredInmillis + TimeUnit.HOURS.toMillis(24);
        }
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        pendingIntent = PendingIntent.getBroadcast(this,0,intent,0);
        Log.d("myApp","timeAlarmTriggeredInmillis: " + timeAlarmTriggeredInmillis);
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,timeAlarmTriggeredInmillis,AlarmManager.INTERVAL_DAY,pendingIntent);
        Utils.postToast("reminder is on", getApplicationContext());
        //save exerciseReminder into db
    }

    //turns off the alarm
    public void cancelAlarm() {
        Intent intent = new Intent(this,AlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(this,0,intent,0);
        if(alarmManager == null) {
            alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        }
        alarmManager.cancel(pendingIntent);
        Utils.postToast("reminder is off", getApplicationContext());
    }

    //set onClickListener for choosing focus area and leads to different exercise page
    @Override
    public void onClick(View v) {
        int theId = v.getId();
        Log.d("myApp", "Onclick:");
        if(theId == R.id.arm) {
            sendAnIntentForChosenFocusedArea(ExerciseSet.ARM);
            UserService.updateCurrSet(db,ExerciseSet.ARM);
        }
        if(theId == R.id.back) {
            sendAnIntentForChosenFocusedArea(ExerciseSet.BACK);
            UserService.updateCurrSet(db,ExerciseSet.BACK);
        }
        if(theId == R.id.leg) {
            sendAnIntentForChosenFocusedArea(ExerciseSet.LEG);
            UserService.updateCurrSet(db,ExerciseSet.LEG);
        }
        if(theId == R.id.setting_reminder_lightExercises) {
            popTimePicker(v);
        }
    }

    //passing chosen selected area by an intent
    public void sendAnIntentForChosenFocusedArea(ExerciseSet chosenFocusArea) {
        Intent intent = new Intent(this,LightExercises_DuringExercise.class);
        intent.putExtra("exercises_focus_area", chosenFocusArea);
        startActivity(intent);
    }

    public void updateLightExerciseInfo(LightExercise lightExercise) {
        AppDatabase appDatabase = AppDatabase.getDbInstance(this.getApplicationContext());
        appDatabase.lightExerciseDao().updateLightExercise(lightExercise);
    }

//    public void loadLightExerciseInfo() {
//        LightExercise lightExercise = getCurrentLightExercise();
//        String date = lightExercise.getDate();
//        ExerciseStatus exerciseStatus = lightExercise.getExerciseStatus();
//        Log.d("Myapp","date: " + date + "exerciseStatus: " + exerciseStatus);
//    }

    //receive step completion request
    public void getStepProgressCompletion(boolean step1Completion, boolean step2Completion,boolean step3Completion,boolean step4Completion) {
        if(step1Completion) {
            stepProgressCompletion.put(1, step1Completion);
        }
        if(step2Completion) {
            stepProgressCompletion.put(2, step2Completion);
        }
        if(step3Completion) {
            stepProgressCompletion.put(3, step3Completion);
        }
        if(step4Completion) {
            stepProgressCompletion.put(4, step4Completion);
        }
    }

    private long convertHourAndMinToMilliSeconds(int hr, int minutes) {
        long millis = TimeUnit.HOURS.toMillis(hr) + TimeUnit.MINUTES.toMillis(minutes);
        Log.d(TAG,"convertHourAndMinToMilliSeconds: " + millis);
        Log.d(TAG,"convertHourAndMinToMilliSeconds, hr: " + hr);
        Log.d(TAG,"convertHourAndMinToMilliSeconds, mins: " + minutes);
        return millis;
    }

    private void convertMiliSecondsToHourAndMin(long milliseconds) {
        hour = (int) (milliseconds / 1000) / 3600;
        Log.d(TAG,"convertMiliSecondsToHourAndMin:  + hour: " + hour);
        min = (int) (milliseconds - hour * 1000*3600)/ 60000;
        if(min == 60) {
            min = 0;
        }
        Log.d(TAG,"convertMiliSecondsToHourAndMin:  + min: " + min);
    }

    private void setProgressBarProgress() {
        stepProgressCompletion.forEach((currentSetPosition, stepCompleted) -> {
            Log.d("myApp","key: " + currentSetPosition + ", value: " + stepCompleted);
            if(currentSetPosition == 1 && stepCompleted) {
                stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.ONE);
            }
            if(currentSetPosition == 2  && stepCompleted) {
                stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.TWO);
            }
            if(currentSetPosition == 3  && stepCompleted) {
                stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.THREE);
            }
            if(currentSetPosition == 4  && stepCompleted) {
                stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.FOUR);
            }
            if(stepProgressCompletion.size() == 4) {
                stateProgressBar.setAllStatesCompleted(true);
                Utils.postToast("All sets completed for today!", getApplicationContext());
            }
        });
    }
}