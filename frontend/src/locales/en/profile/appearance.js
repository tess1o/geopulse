/**
 * Appearance & accessibility tab: map colors, path width/outline, speed bands and heatmap gradient.
 */
export default {
    title: 'Appearance & accessibility',
    description: 'Choose map colors and line styles that are easy for you to see, including color vision friendly presets.',
    colorVision: {
        heading: 'Color vision',
        description: 'Pick a preset that sets path, speed and heatmap colors in one step. You can still fine-tune individual colors below.',
        customized: 'Customized',
        schemes: {
            DEFAULT: {
                name: 'Default',
                description: 'The original GeoPulse colors.'
            },
            RED_GREEN_SAFE: {
                name: 'Red-green safe',
                description: 'For deuteranopia and protanopia. Uses blue and yellow instead of red and green.'
            },
            BLUE_YELLOW_SAFE: {
                name: 'Blue-yellow safe',
                description: 'For tritanopia. Uses red and teal instead of blue and yellow.'
            },
            HIGH_CONTRAST: {
                name: 'High contrast',
                description: 'Strong, distinct colors that also differ in brightness. Works for all color vision types.'
            }
        }
    },
    preview: {
        heading: 'Preview',
        description: 'How paths look on light, dark and satellite maps with your current (unsaved) choices.',
        ariaLabel: 'Map path preview on light, dark and satellite backgrounds',
        backgrounds: {
            light: 'Light map',
            dark: 'Dark map',
            satellite: 'Satellite'
        },
        simulate: {
            label: 'Simulate',
            none: 'Normal vision',
            deuteranopia: 'Deuteranopia',
            protanopia: 'Protanopia',
            tritanopia: 'Tritanopia'
        }
    },
    paths: {
        heading: 'Paths',
        description: 'Colors and line style used to draw trips on the map.',
        followScheme: 'From color scheme',
        resetColor: 'Use scheme color',
        defaultPathColor: {
            title: 'Path color',
            description: 'Color used for trip lines that are not selected.',
            details: 'Leave unset to follow the color scheme.'
        },
        activePathColor: {
            title: 'Selected trip color',
            description: 'Color used for the highlighted line when you select a trip.',
            details: 'Leave unset to follow the color scheme. Driving trips use speed colors unless speed colors are turned off.'
        },
        pathWidth: {
            title: 'Line width',
            description: 'Thickness of trip lines. The selected trip is drawn slightly thicker.',
            details: 'Thicker lines are easier to see on busy or satellite maps.'
        },
        outline: {
            title: 'Line outline',
            description: 'Draw a contrasting border around trip lines.',
            details: 'Keeps lines visible on any background, whatever their color.'
        },
        widthLabels: {
            thin: 'Thin',
            default: 'Default',
            thick: 'Thick'
        }
    },
    speedBands: {
        heading: 'Driving speed colors',
        description: 'A selected driving trip is colored by speed so you can spot slow and fast sections.',
        palette: {
            title: 'Speed colors',
            description: 'Colors for slow, medium and fast sections of a driving trip.',
            details: 'Slow is under {slow} {unit}, medium is {slow}–{medium} {unit}, fast is over {medium} {unit}. Turn off to draw driving trips in the selected trip color.',
            followScheme: 'Follow color scheme ({name})'
        },
        options: {
            DEFAULT: 'Red / amber / green',
            RED_GREEN_SAFE: 'Violet / sky / yellow',
            BLUE_YELLOW_SAFE: 'Red / salmon / teal',
            HIGH_CONTRAST: 'Blue / rose / gold',
            OFF: 'Off (single color)'
        }
    },
    heatmap: {
        heading: 'Heatmap',
        description: 'Colors used for the heatmap on the timeline map and in digests.',
        gradient: {
            title: 'Heatmap colors',
            description: 'Gradient from fewer to more visits.',
            details: 'Viridis and Cividis stay readable for all color vision types and in greyscale.',
            followScheme: 'Follow color scheme ({name})'
        },
        options: {
            CLASSIC: 'Classic (blue to red)',
            VIRIDIS: 'Viridis (purple to yellow)',
            CIVIDIS: 'Cividis (navy to yellow)'
        }
    },
    resetToDefaults: 'Reset to Defaults',
    saveChanges: 'Save Changes'
}
