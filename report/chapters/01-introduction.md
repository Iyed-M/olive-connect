# Introduction

OliveConnect is a smart olive-collection platform for a cooperative in the
Sfax region. It coordinates the work that begins with a Farmer's orchard and
harvest request, continues through collection planning and field execution,
and ends with Mill reception and operational reporting.

The project deliberately uses a modular monolith: a Spring Boot REST API, a
MongoDB document store, Redis for short-lived state and fan-out, and a React
single-page application served by Nginx. This keeps the deployment simple for
the team while preserving clear feature packages and the Controller → Service
→ DAO → Entity layering required by the subject.

This report is the M14 defence artifact. Its design claims are derived from
[`docs/01–11`](../../docs/), while runtime claims are backed by the generated
evidence directory. The terminology follows the shared [domain glossary](../../CONTEXT.md).

## Objectives

The platform must make the collection flow visible and traceable, protect each
actor's data according to Role, provide a useful public map, integrate external
datasets, and remain operable in a small production deployment. The final
deliverable therefore includes the design, implementation choices, tests,
deployment procedure, report build, and a timed demonstration.
