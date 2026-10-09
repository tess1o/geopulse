/**
 * General tab (ProfileTab.vue): identity, regional preferences, and the default landing page.
 *
 * EN values are verbatim from the previous literals -- profileTabsDirtyState.test.js asserts
 * 'General', 'Profile', 'Regional preferences' and 'Navigation' exactly.
 *
 * The destination picker reuses the `nav.items.*` keys rather than restating the names: the entries
 * are the same destinations the navigation shows, so a shared key keeps the two in step. Only
 * 'Custom URL...' is specific to this picker.
 *
 * Timezone city names are deliberately NOT in the catalog -- they are IANA identifiers shown in Latin
 * script by convention, and translating them would make the stored value harder to match.
 */
export default {
    title: 'General',
    description: 'Manage your identity and everyday display preferences.',
    profile: {
        heading: 'Profile',
        description: 'Choose the name and image shown across GeoPulse.',
        fullName: {
            title: 'Full name',
            description: 'The name shown on your account.',
            placeholder: 'Enter your full name'
        },
        image: {
            heading: 'Profile image',
            optional: 'Optional',
            description: 'Customize the image used for map markers.',
            upload: 'Upload Custom Avatar',
            formats: 'PNG, JPEG, or WEBP; resized automatically before upload.',
            selected: 'Selected: {name}',
            chooseAria: 'Choose profile image {index}'
        }
    },
    regional: {
        heading: 'Regional preferences',
        description: 'Control timezone, formats, and measurement units.',
        timezone: {
            title: 'Timezone',
            description: 'Used for dates, times, and statistics.',
            placeholder: 'Select your timezone'
        },
        dateFormat: {
            title: 'Date format',
            description: 'Choose how numeric dates appear.',
            details: 'URL date parameters continue to use a stable ISO format.',
            placeholder: 'Select your preferred date format'
        },
        timeFormat: {
            title: 'Time format',
            description: 'Choose whether times use a 12- or 24-hour clock.',
            placeholder: 'Select your preferred time format'
        },
        timeDisplayMode: {
            title: 'Timeline time zone',
            description: 'Show timeline and GPS data times in your profile timezone, or in the local time where each place was visited.',
            details: 'Days are still grouped by your profile timezone. The local timezone comes from the nearest city, so times near timezone borders may be approximate. Hover a time to see where its timezone came from.',
            unavailable: 'Local time is unavailable because GeoNames city data is not loaded on this server. Times are shown in your profile timezone.'
        },
        distanceUnit: {
            title: 'Distance unit',
            description: 'Controls displayed distances and speeds.',
            placeholder: 'Select your distance unit'
        },
        temperatureUnit: {
            title: 'Temperature unit',
            description: 'Controls temperatures shown in weather views.',
            placeholder: 'Select your temperature unit'
        }
    },
    navigation: {
        heading: 'Navigation',
        description: 'Choose where GeoPulse opens after sign-in.',
        homePage: {
            title: 'Default home page',
            description: 'Select the first page shown after sign-in.',
            details: "Clear the selection to use GeoPulse's default behavior. Custom destinations must be internal paths beginning with /.",
            placeholder: 'Select your default page',
            customOption: 'Custom URL...',
            customLabel: 'Custom internal path',
            customPlaceholder: '/app/your-custom-page'
        }
    },
    dateFormatOptions: {
        dmy: 'DD/MM/YYYY (European)',
        mdy: 'MM/DD/YYYY (US)',
        ymd: 'YYYY-MM-DD (ISO)'
    },
    timeFormatOptions: {
        h24: '24-hour (13:45)',
        h12: '12-hour (1:45 PM)'
    },
    timeDisplayModeOptions: {
        profile: 'Profile timezone',
        location: 'Local time at each place'
    },
    distanceUnitOptions: {
        kilometers: 'Kilometers (km, m)',
        miles: 'Miles (mi, ft)'
    },
    temperatureUnitOptions: {
        celsius: 'Celsius (°C)',
        fahrenheit: 'Fahrenheit (°F)'
    },
    actions: {
        reset: 'Reset',
        save: 'Save Changes'
    },
    errors: {
        fullNameRequired: 'Full name is required',
        fullNameTooShort: 'Full name must be at least 2 characters',
        customUrlRequired: 'Custom URL is required',
        customUrlInternal: 'URL must be an internal path starting with /',
        customUrlInvalid: 'Invalid URL format',
        customUrlTooLong: 'URL is too long (max 1000 characters)',
        imageLoad: 'Failed to load image',
        imageTooLarge: 'Image is too large after compression. Try a different image.',
        imageFormat: 'Unsupported image format. Use PNG, JPEG, or WEBP.',
        imageUnsupported: 'Image processing is not supported in this browser',
        imageProcess: 'Unable to process selected image',
        avatarTooLarge: 'Avatar is too large after compression'
    }
}
