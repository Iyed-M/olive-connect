# Implementation and user interface

The React application is organized by feature and uses TanStack Router with a
shared authenticated layout. `RequireRole` guards routes while the API repeats
the authorization decision. The active Role changes navigation and the role
home, never the permissions in the JWT.

## Role-facing surfaces

The Farmer has orchard, request, alert and reception pages. The Collection
manager has the request queue, planning board, alert board, intranet grids,
calendar and dashboard. The Team leader has the mobile round runner. The
Logistics manager has worker, equipment, calendar and supply pages. The Mill
manager has incoming rounds, reception and capacity pages. The Admin has user
and registration administration, imports, settings and shared operational
pages. The public visitor has the map and registration request.

The layout uses a side navigation on desktop and a bottom navigation on small
screens. Contextual help is selected by route and Role; the guided tour marks
the navigation, work area, help control and profile menu on first login. The
route catalogue and wireframes are in
[`docs/08-frontend.md`](../../docs/08-frontend.md). The screenshot evidence
runner can exercise configured accounts at both mobile and desktop viewports.
