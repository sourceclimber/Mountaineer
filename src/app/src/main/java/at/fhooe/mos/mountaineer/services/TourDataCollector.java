package at.fhooe.mos.mountaineer.services;

import android.os.Handler;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import at.fhooe.mos.mountaineer.model.calculations.EnergyExpenditureCalculator;
import at.fhooe.mos.mountaineer.model.tour.LocationPoint;
import at.fhooe.mos.mountaineer.model.tour.Tour;
import at.fhooe.mos.mountaineer.model.user.UserInformation;
import at.fhooe.mos.mountaineer.model.weather.Weather;
import at.fhooe.mos.mountaineer.sensors.heartrate.HeartRateSensorEventListener;
import at.fhooe.mos.mountaineer.sensors.location.LocationSensorEventListener;
import at.fhooe.mos.mountaineer.sensors.stepsensor.StepSensorEventListener;
import at.fhooe.mos.mountaineer.sensors.stopwatch.StopwatchEventListener;
import at.fhooe.mos.mountaineer.webservices.OpenWeatherMap;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by stefan on 25.11.2017.
 */

public class TourDataCollector implements
        StepSensorEventListener,
        StopwatchEventListener,
        LocationSensorEventListener,
        HeartRateSensorEventListener {

    private static final int PERIODIC_SUMMATION_TIME_S = 10;
    private static final int PERIODIC_SUMMATION_TIME_MS = PERIODIC_SUMMATION_TIME_S * 1000;

    private Handler handler;
    private PeriodicSummation periodicSummation;

    private boolean publishTourDataUpdates;

    private Tour tour;

    private double heartRateSum = 0;
    private int heartRateSumCount = 0;
    private int stepCountSum = 0;

    private boolean weatherFetched = false;

    public TourDataCollector() {
        tour = new Tour();
        publishTourDataUpdates = true;

        handler = new Handler();
        periodicSummation = new PeriodicSummation();

        EventBus.getDefault().register(this);
    }

    public void start(UserInformation userInformation) {
        tour.setUserInformation(userInformation);
        startPeriodicSummation();
    }

    public void stop() {
        stopPeriodicSummation();
    }

    @Override
    public void onStepDetectedEvent() {
        tour.setTotalSteps(tour.getTotalSteps() + 1);
        stepCountSum++;

        publishData();
    }

    @Override
    public void onElapsedSecondsEvent(long elapsedSeconds) {
        tour.setDuration(elapsedSeconds);
        publishData();
    }

    @Override
    public void onFinalTimeEvent(long startTimestamp, long stopTimestamp, long elapsedSeconds) {
        tour.setStartTimestamp(startTimestamp);
        tour.setStopTimestamp(stopTimestamp);
        tour.setDuration(elapsedSeconds);
    }

    @Override
    public void onLocationReceivedEvent(double latitude, double longitude, double altitude) {
        if (tour.getStartLocation() == null) {
            tour.setStartLocation(new LocationPoint(latitude, longitude, altitude));
        }

        tour.getTourDetails().addLocationPointAtTime(tour.getDuration(), new LocationPoint(latitude, longitude, altitude));

        publishData();

        if (weatherFetched == false) {
            OpenWeatherMap.fetchWeather(latitude, longitude, new Callback<Weather>() {
                @Override
                public void onResponse(Call<Weather> call, Response<Weather> response) {
                    tour.setWeather(response.body());
                    publishData();
                    weatherFetched = true;
                }

                @Override
                public void onFailure(Call<Weather> call, Throwable t) {
                    Log.e("TourDataCollector", "Could not fetch weather data!\n" + t.getMessage());
                }
            });
        }
    }

    @Override
    public void onHeatRateEvent(double heartRate) {
        heartRateSum += heartRate;
        heartRateSumCount++;

        tour.setCurrentHeartRate(heartRate);
    }

    @Subscribe
    public void onMessageEvent(ControlEvent event) {
        publishTourDataUpdates = event.getPublishTourDataUpdates();
    }

    public void publishFinalTourData() {
        EventBus.getDefault().removeStickyEvent(FinalTourDataEvent.class);
        EventBus.getDefault().postSticky(new FinalTourDataEvent(tour));
    }

    public Tour getTour() {
        return tour;
    }

    private void publishData() {
        if (publishTourDataUpdates) {
            EventBus.getDefault().post(new TourDataUpdateEvent(tour));
        }
    }

    public static class TourDataUpdateEvent {
        private Tour tour;

        protected TourDataUpdateEvent(Tour tour) {
            this.tour = tour;
        }

        public Tour getTour() {
            return tour;
        }
    }

    public static class FinalTourDataEvent {
        private Tour tour;

        protected FinalTourDataEvent(Tour tour) {
            this.tour = tour;
        }

        public Tour getTour() {
            return tour;
        }
    }

    public static class ControlEvent {
        private boolean publishTourDataUpdates;

        public ControlEvent(Boolean publishTourDataUpdates) {
            this.publishTourDataUpdates = publishTourDataUpdates;
        }

        public boolean getPublishTourDataUpdates() {
            return publishTourDataUpdates;
        }
    }

    private void startPeriodicSummation() {
        handler.postDelayed(periodicSummation, PERIODIC_SUMMATION_TIME_MS);
    }

    private void stopPeriodicSummation() {
        handler.removeCallbacks(periodicSummation);
    }

    private class PeriodicSummation implements Runnable {
        @Override
        public void run() {
            tour.getTourDetails().addStepCountAtTime(tour.getDuration(), stepCountSum);
            tour.setAverageSteps(stepCountSum);
            tour.setStepFrequency(stepCountSum / PERIODIC_SUMMATION_TIME_MS * 60);

            int strideLength = tour.getUserInformation().getStrideLength();
            int strideDistance = stepCountSum * strideLength;
            strideDistance = strideDistance / 100; // cm -> m
            tour.setDistanceFromSteps(strideDistance + tour.getDistanceFromSteps());

            double speedFromSteps = ((double)strideDistance / PERIODIC_SUMMATION_TIME_S);
            speedFromSteps = speedFromSteps * 3.6; // m/s -> km/h
            tour.setSpeedFromSteps(speedFromSteps);

            double energyExpenditureFromSteps = tour.getUserInformation().getAge() * 4.1 * strideDistance/100;
            double energyExpenditureFromStepsKcal = energyExpenditureFromSteps / 4.2;
            tour.setEnergyExpenditureFromSteps(energyExpenditureFromStepsKcal + tour.getEnergyExpenditureFromSteps());

            double cadence =  ((double)stepCountSum / PERIODIC_SUMMATION_TIME_S);
            cadence = cadence * 60;
            tour.setCadence((int)cadence);

            stepCountSum = 0;

            double averageHeartRateInPeriod = heartRateSum / heartRateSumCount;
            averageHeartRateInPeriod = Math.floor(averageHeartRateInPeriod * 100) / 100;

            double energyExpenditureInPeriod = EnergyExpenditureCalculator.calculateEnergyExpenditureEstimation(tour.getUserInformation(), averageHeartRateInPeriod);

            tour.setBurnedKcal(tour.getBurnedKcal() + energyExpenditureInPeriod);

            tour.getTourDetails().addHeartRateAtTime(tour.getDuration(), averageHeartRateInPeriod);
            tour.getTourDetails().addEnergyExpenditureAtTime(tour.getDuration(), energyExpenditureInPeriod);

            heartRateSum = 0;
            heartRateSumCount = 0;

            handler.postDelayed(this, PERIODIC_SUMMATION_TIME_MS);
        }
    }
}
