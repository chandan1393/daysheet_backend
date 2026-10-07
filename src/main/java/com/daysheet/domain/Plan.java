package com.daysheet.domain;

/** TRIAL on signup, ACTIVE once paid, EXPIRED when the trial or paid period runs out. SOLO and PRACTICE are kept for old rows. */
public enum Plan { TRIAL, ACTIVE, SOLO, PRACTICE, EXPIRED }
