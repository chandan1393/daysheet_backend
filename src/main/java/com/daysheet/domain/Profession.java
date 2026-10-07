package com.daysheet.domain;

import java.util.List;

/**
 * Each profession is a preset: the words the app uses and the services a new
 * workspace starts with. Everything here can be changed later in Settings.
 */
public enum Profession {
    DOCTOR("Doctor", "Patient", "Patients", "Consultation", List.of(
            new ServicePreset("General consultation", 15, 30, "#0E7C86"),
            new ServicePreset("Follow-up visit", 10, 20, "#4C9A6A"),
            new ServicePreset("Health check-up", 30, 60, "#6D5DF6"))),
    PHYSIOTHERAPIST("Physiotherapist", "Patient", "Patients", "Session", List.of(
            new ServicePreset("Initial assessment", 60, 60, "#0E7C86"),
            new ServicePreset("Treatment session", 45, 45, "#6D5DF6"),
            new ServicePreset("Sports massage", 30, 35, "#E5487A"))),
    DENTIST("Dentist", "Patient", "Patients", "Appointment", List.of(
            new ServicePreset("Check-up and clean", 30, 50, "#0E7C86"),
            new ServicePreset("Filling", 45, 90, "#6D5DF6"),
            new ServicePreset("Whitening", 60, 150, "#F2A20C"))),
    PSYCHOLOGIST("Psychologist / Therapist", "Client", "Clients", "Session", List.of(
            new ServicePreset("Intake session", 60, 80, "#0E7C86"),
            new ServicePreset("Therapy session", 50, 70, "#6D5DF6"),
            new ServicePreset("Couples session", 80, 110, "#E5487A"))),
    LAWYER("Lawyer", "Client", "Clients", "Consultation", List.of(
            new ServicePreset("Initial consultation", 30, 50, "#0E7C86"),
            new ServicePreset("Case review", 60, 150, "#6D5DF6"),
            new ServicePreset("Document drafting", 90, 200, "#F2A20C"))),
    ACCOUNTANT("Accountant / CA", "Client", "Clients", "Meeting", List.of(
            new ServicePreset("Tax consultation", 45, 60, "#0E7C86"),
            new ServicePreset("Bookkeeping review", 60, 90, "#4C9A6A"),
            new ServicePreset("Business advisory", 60, 120, "#6D5DF6"))),
    CONSULTANT("Consultant / Coach", "Client", "Clients", "Session", List.of(
            new ServicePreset("Discovery call", 30, 0, "#4C9A6A"),
            new ServicePreset("Coaching session", 60, 90, "#6D5DF6"),
            new ServicePreset("Strategy workshop", 120, 250, "#0E7C86"))),
    TUTOR("Tutor / Trainer", "Student", "Students", "Lesson", List.of(
            new ServicePreset("Trial lesson", 30, 0, "#4C9A6A"),
            new ServicePreset("One-to-one lesson", 60, 30, "#6D5DF6"),
            new ServicePreset("Exam preparation", 90, 45, "#F2A20C"))),
    SALON("Salon / Beauty", "Client", "Clients", "Appointment", List.of(
            new ServicePreset("Haircut", 45, 30, "#E5487A"),
            new ServicePreset("Colour", 120, 90, "#6D5DF6"),
            new ServicePreset("Facial", 60, 50, "#0E7C86"))),
    OTHER("Something else", "Client", "Clients", "Appointment", List.of(
            new ServicePreset("Consultation", 30, 40, "#0E7C86"),
            new ServicePreset("Standard session", 60, 70, "#6D5DF6")));

    public record ServicePreset(String name, int minutes, int basePrice, String color) {}

    private final String displayName;
    private final String clientLabel;
    private final String clientLabelPlural;
    private final String sessionLabel;
    private final List<ServicePreset> services;

    Profession(String displayName, String clientLabel, String clientLabelPlural,
               String sessionLabel, List<ServicePreset> services) {
        this.displayName = displayName;
        this.clientLabel = clientLabel;
        this.clientLabelPlural = clientLabelPlural;
        this.sessionLabel = sessionLabel;
        this.services = services;
    }

    public String displayName() { return displayName; }
    public String clientLabel() { return clientLabel; }
    public String clientLabelPlural() { return clientLabelPlural; }
    public String sessionLabel() { return sessionLabel; }
    public List<ServicePreset> services() { return services; }
}
