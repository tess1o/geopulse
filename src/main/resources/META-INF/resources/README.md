# GeoPulse Frontend

A lightweight, responsive frontend for the GeoPulse application that allows users to track and visualize their location data.

## Features

- **User Authentication**: Login and registration functionality
- **Location Visualization**: Interactive map showing location paths
- **Date Selection**: View location data for different time periods
  - Today
  - Yesterday
  - Last 7 days
  - Last 30 days
  - Custom date range
- **Path Statistics**: View statistics about your location path
  - Number of data points
  - Time span
  - Average speed
  - Total distance

## Structure

The frontend is built with vanilla JavaScript, HTML, and CSS, making it lightweight and fast. It uses the Leaflet.js library for map visualization.

### Files

- **HTML**
  - `index.html`: Entry point that redirects to login or dashboard
  - `login.html`: Login page
  - `register.html`: Registration page
  - `dashboard.html`: Main application page with map and controls

- **CSS**
  - `css/styles.css`: All styles for the application

- **JavaScript**
  - `js/auth.js`: Authentication module (login, logout, registration)
  - `js/index.js`: Entry point script
  - `js/login.js`: Login page functionality
  - `js/register.js`: Registration page functionality
  - `js/dashboard.js`: Dashboard functionality (map, data fetching, statistics)

## Authentication

The application uses Basic Authentication. Credentials are stored in the browser's localStorage and sent with each API request. The authentication flow is:

1. User enters credentials on login page
2. Credentials are validated against the backend API
3. If valid, credentials are stored in localStorage
4. Authenticated requests include an Authorization header

## API Integration

The frontend communicates with the backend API using the following endpoints:

- `POST /api/users/register`: Register a new user
- `GET /api/users/me`: Get the current authenticated user
- `GET /api/locations/path`: Get location path data for a time period

## Future Extensions

The frontend is designed to be easily extensible. Here are some potential future enhancements:

1. **Statistics Dashboard**: Add more detailed statistics and visualizations
2. **Data Export**: Allow users to export their location data in various formats
3. **Real-time Updates**: Add WebSocket support for real-time location updates
4. **Filtering Options**: Add more filtering options for location data
5. **User Settings**: Allow users to customize their experience
6. **Mobile App**: Convert to a Progressive Web App (PWA) for mobile use

## Development

To modify or extend the frontend:

1. Add new HTML files for additional pages
2. Update the CSS in `styles.css` to maintain consistent styling
3. Add new JavaScript modules as needed
4. Extend the Auth module in `auth.js` for additional authentication features
5. Update the dashboard in `dashboard.js` for new visualization features