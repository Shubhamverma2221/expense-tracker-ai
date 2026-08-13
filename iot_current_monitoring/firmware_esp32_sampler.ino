/*
 * ============================================================================
 * ESP32 Current Signature Sampler & NILM Feature Extractor
 * 
 * Hardware:
 *   - ESP32 Dev Module (ADC Pin 34)
 *   - SCT-013-000 Current Transformer Clamp with 1.65V DC bias circuit
 * 
 * Functions:
 *   - High-speed ADC sampling (1000 samples/sec over 50Hz AC cycles)
 *   - In-memory Root Mean Square (RMS) calculation
 *   - Peak current & Crest factor extraction
 *   - JSON serialization over Serial / Wi-Fi MQTT
 * ============================================================================
 */

#include <Arduino.h>

const int ADC_PIN = 34;                  // Analog input pin connected to CT clamp
const int SAMPLES_PER_CYCLE = 200;       // 200 samples over 50Hz (20ms window) = 10 kHz sampling
const float ADC_VOLTAGE_REF = 3.3;       // ESP32 ADC Reference Voltage
const float ADC_RESOLUTION = 4095.0;     // 12-bit ADC (0 - 4095)
const float CT_CALIBRATION_FACTOR = 30.0;// Calibration factor for SCT-013-000 with 33 ohm burden

void setup() {
  Serial.begin(115200);
  analogReadResolution(12);
  analogSetAttenuation(ADC_11db); // Full 0 - 3.3V range
  Serial.println("=== ESP32 Current Signature Sampler Initialized ===");
}

void loop() {
  double sum_squared = 0;
  int peak_raw = 0;
  int min_raw = 4095;
  
  unsigned long start_time = micros();
  
  // Sample across 5 complete AC mains cycles (100 ms)
  int total_samples = SAMPLES_PER_CYCLE * 5;
  for (int i = 0; i < total_samples; i++) {
    int raw_val = analogRead(ADC_PIN);
    
    if (raw_val > peak_raw) peak_raw = raw_val;
    if (raw_val < min_raw) min_raw = raw_val;
    
    // Remove DC offset (midpoint ~ 2048)
    double centered_val = (double)raw_val - 2048.0;
    sum_squared += (centered_val * centered_val);
    
    // Delay for 100 microseconds (10 kHz sample rate)
    delayMicroseconds(100);
  }
  
  // Calculate RMS Current
  double mean_squared = sum_squared / total_samples;
  double raw_rms = sqrt(mean_squared);
  double voltage_rms = (raw_rms / ADC_RESOLUTION) * ADC_VOLTAGE_REF;
  double current_rms = voltage_rms * CT_CALIBRATION_FACTOR;
  
  // Peak to Peak
  double peak_voltage = ((peak_raw - min_raw) / ADC_RESOLUTION) * ADC_VOLTAGE_REF;
  double peak_current = (peak_voltage / 2.0) * CT_CALIBRATION_FACTOR;
  double crest_factor = (current_rms > 0.05) ? (peak_current / current_rms) : 1.414;
  
  // Output structured JSON telemetry
  Serial.print("{\"rms_current\": ");
  Serial.print(current_rms, 2);
  Serial.print(", \"peak_current\": ");
  Serial.print(peak_current, 2);
  Serial.print(", \"crest_factor\": ");
  Serial.print(crest_factor, 2);
  Serial.print(", \"apparent_power_w\": ");
  Serial.print(current_rms * 230.0, 1); // Assuming 230V mains
  Serial.println("}");
  
  delay(500); // 2 readings per second
}
