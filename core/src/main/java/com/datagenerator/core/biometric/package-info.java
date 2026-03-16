/*
 * Copyright 2026 Marco Ferretti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Biometric record validation for ISO/IEC 19794 fingerprint and face data.
 *
 * <p>The central class is {@link com.datagenerator.core.biometric.BiometricValidator}, which
 * dispatches records to modality-specific rule lists:
 *
 * <ul>
 *   <li>{@link com.datagenerator.core.biometric.FingerprintMinutiaeRules} — ISO/IEC 19794-2
 *   <li>{@link com.datagenerator.core.biometric.FaceImageRules} — ISO/IEC 19794-5
 * </ul>
 *
 * <p>Validation results are captured in {@link com.datagenerator.core.biometric.ValidationReport}
 * and {@link com.datagenerator.core.biometric.RecordViolation}.
 *
 * @since 1.0
 */
package com.datagenerator.core.biometric;
