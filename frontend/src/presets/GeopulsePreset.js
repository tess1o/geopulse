import {definePreset} from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/*
 * GeoPulse theme = Aura + brand blue + one neutral palette (slate) in both color schemes.
 *
 * This preset is the single source of truth for colors. The --gp-* tokens in src/styles/tokens.css alias the
 * semantic tokens emitted from here (--p-primary-color, --p-content-background, --p-text-color, ...), so PrimeVue
 * components and custom GeoPulse UI always agree. Theme PrimeVue components here (semantic or component tokens),
 * not with `.p-dark .p-xxx { ... !important }` CSS overrides.
 *
 * Keep the surface palette in Aura's orientation (0 = white ... 950 = darkest) in BOTH schemes: every Aura
 * component token assumes it (e.g. dark formField.background = {surface.950}, dark text.color = {surface.0}).
 */
const GeopulsePreset = definePreset(Aura, {
    semantic: {
        primary: {
            50: '#eff6ff',
            100: '#dbeafe',
            200: '#bfdbfe',
            300: '#93c5fd',
            400: '#60a5fa',
            500: '#3b82f6',
            600: '#1a56db', // Main GeoPulse brand color
            700: '#1d4ed8',
            800: '#1e40af',
            900: '#1e3a8a',
            950: '#172554'
        },
        colorScheme: {
            light: {
                // surface: Aura's light default is already slate.
                primary: {
                    color: '{primary.600}',
                    contrastColor: '#ffffff',
                    hoverColor: '{primary.700}',
                    activeColor: '{primary.800}'
                },
                highlight: {
                    background: '{primary.600}',
                    focusBackground: '{primary.700}',
                    color: '#ffffff',
                    focusColor: '#ffffff'
                },
                text: {
                    color: '{surface.800}',
                    hoverColor: '{surface.900}',
                    mutedColor: '{surface.500}',
                    hoverMutedColor: '{surface.600}'
                }
            },
            dark: {
                surface: {
                    0: '#ffffff',
                    50: '{slate.50}',
                    100: '{slate.100}',
                    200: '{slate.200}',
                    300: '{slate.300}',
                    400: '{slate.400}',
                    500: '{slate.500}',
                    600: '{slate.600}',
                    700: '{slate.700}',
                    800: '{slate.800}',
                    900: '{slate.900}',
                    950: '{slate.950}'
                },
                primary: {
                    color: '{primary.500}',
                    contrastColor: '#ffffff',
                    hoverColor: '{primary.400}',
                    activeColor: '{primary.300}'
                },
                highlight: {
                    background: '{primary.500}',
                    focusBackground: '{primary.600}',
                    color: '#ffffff',
                    focusColor: '#ffffff'
                },
                formField: {
                    background: '{surface.900}',
                    disabledBackground: '{surface.800}',
                    filledBackground: '{surface.800}',
                    filledHoverBackground: '{surface.800}',
                    filledFocusBackground: '{surface.800}',
                    borderColor: '{surface.600}',
                    hoverBorderColor: '{surface.500}',
                    color: '{surface.100}'
                },
                text: {
                    color: '{surface.100}',
                    hoverColor: '{surface.0}',
                    mutedColor: '{surface.300}',
                    hoverMutedColor: '{surface.200}'
                },
                content: {
                    background: '{surface.800}',
                    hoverBackground: '{surface.700}',
                    borderColor: '{surface.700}'
                },
                overlay: {
                    select: {background: '{surface.800}', borderColor: '{surface.700}'},
                    popover: {background: '{surface.800}', borderColor: '{surface.700}'},
                    modal: {background: '{surface.800}', borderColor: '{surface.700}'}
                },
                list: {
                    option: {focusBackground: '{surface.700}'}
                },
                navigation: {
                    item: {focusBackground: '{surface.700}', activeBackground: '{surface.700}'}
                }
            }
        }
    },
    components: {
        button: {
            root: {borderRadius: '{border.radius.lg}'},
            colorScheme: {
                light: {
                    root: {
                        // GeoPulse uses emerald for the "secondary" severity in light mode.
                        secondary: {
                            background: '{emerald.500}',
                            hoverBackground: '{emerald.600}',
                            activeBackground: '{emerald.700}',
                            borderColor: '{emerald.500}',
                            color: '#ffffff'
                        }
                    }
                }
            }
        },
        card: {
            colorScheme: {
                light: {
                    root: {
                        shadow: '0 1px 3px 0 rgba(26, 86, 219, 0.1), 0 1px 2px 0 rgba(26, 86, 219, 0.06)'
                    }
                }
            }
        },
        datatable: {
            colorScheme: {
                light: {
                    header: {background: '{surface.50}'},
                    headerCell: {background: '{surface.50}'},
                    footer: {background: '{surface.50}'},
                    footerCell: {background: '{surface.50}'}
                },
                dark: {
                    root: {borderColor: '{content.border.color}'},
                    header: {background: '{surface.900}'},
                    headerCell: {background: '{surface.900}'},
                    footer: {background: '{surface.900}'},
                    footerCell: {background: '{surface.900}'},
                    row: {stripedBackground: '{surface.900}'}
                }
            }
        },
        // "Today" in emerald (GeoPulse secondary) so it stays distinct from the primary-blue selected date.
        // A selected today still shows as selected: PrimeVue's `.p-datepicker-today > .p-datepicker-day-selected` wins.
        datepicker: {
            colorScheme: {
                light: {today: {background: '{emerald.500}', color: '#ffffff'}},
                dark: {today: {background: '{emerald.500}', color: '#ffffff'}}
            }
        },
        toast: {
            root: {borderRadius: '{border.radius.lg}'},
            content: {padding: '1rem'},
            summary: {fontWeight: '600', fontSize: '0.875rem'},
            detail: {fontWeight: '400', fontSize: '0.8rem'}
        },
        // Tooltips are inverted relative to the page in both schemes.
        tooltip: {
            colorScheme: {
                light: {root: {background: '{surface.800}', color: '#ffffff'}},
                dark: {root: {background: '{surface.50}', color: '{surface.800}'}}
            }
        }
    }
});

export default GeopulsePreset;
