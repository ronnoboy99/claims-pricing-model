# Claims Pricing Model

A general insurance claims pricing model built on the freMTPL2 motor dataset.

**Status:** Work in progress — just started.

Built by Aaron Ranjith, a first-year Actuarial Science student at Monash University.

## About the project

This project builds a frequency–severity pricing model for motor insurance using the freMTPL2 dataset, which contains about 670,000 policies.

The aim is to estimate a pure premium for each policy by modelling how often claims occur and how large they are when they do, then to present the results in a Power BI dashboard.

## Method

- **Frequency:** Poisson generalised linear model (GLM) for claim counts
- **Severity:** Gamma GLM for claim amounts
- **Pure premium:** Frequency × severity
- **Benchmark:** XGBoost, compared against the GLM results

## Tech stack

- R
- Python
- Power BI

## Roadmap

- [ ] **29 Nov 2026** — Download and clean the freMTPL2 data
- [ ] **1 Dec 2026** — Fit the Poisson GLM for claim frequency
- [ ] **3 Dec 2026** — Fit the Gamma GLM for severity and combine into a pure premium
- [ ] **After that** — Compare GLM vs XGBoost and build the Power BI dashboard
