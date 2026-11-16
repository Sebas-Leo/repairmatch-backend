package com.repairmatch.repairmatch_backend.model;

/** Where a user stands in the (optional) identity verification flow. Never blocks using the app. */
public enum IdentityStatus { NOT_STARTED, PENDING, VERIFIED, REJECTED }
