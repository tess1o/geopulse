import {definePreset} from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

const GeopulsePreset = definePreset(Aura, {
    semantic: {
        // Primary color - GeoPulse brand blue
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
                primary: {
                    color: '#1a56db',
                    inverseColor: '#ffffff',
                    hoverColor: '#1d4ed8',
                    activeColor: '#1e40af'
                },
                highlight: {
                    background: '#1a56db',
                    focusBackground: '#1d4ed8',
                    color: '#ffffff',
                    focusColor: '#ffffff'
                },
                // Add more colorful surface options
                surface: {
                    0: '#ffffff',
                    50: '#eff6ff',
                    100: '{neutral.100}',
                    200: '{neutral.200}',
                    300: '{neutral.300}',
                    400: '{neutral.400}',
                    500: '{neutral.500}',
                    600: '{neutral.600}',
                    700: '{neutral.700}',
                    800: '{neutral.800}',
                    900: '{neutral.900}',
                    950: '{neutral.950}'
                }
            },
            dark: {
                primary: {
                    color: '#60a5fa',
                    inverseColor: '#172554',
                    hoverColor: '#93c5fd',
                    activeColor: '#bfdbfe'
                },
                highlight: {
                    background: 'rgba(26, 86, 219, .16)',
                    focusBackground: 'rgba(26, 86, 219, .24)',
                    color: 'rgba(255,255,255,.87)',
                    focusColor: 'rgba(255,255,255,.87)'
                },
                surface: {
                    0: '{zinc.950}',
                    50: '{zinc.900}',
                    100: '{zinc.800}',
                    200: '{zinc.700}',
                    300: '{zinc.600}',
                    400: '{zinc.500}',
                    500: '{zinc.400}',
                    600: '{zinc.300}',
                    700: '{zinc.200}',
                    800: '{zinc.100}',
                    900: '{zinc.50}',
                    950: '#ffffff'
                }
            }
        }
    },
    components: {
        button: {
            colorScheme: {
                light: {
                    root: {
                        primary: {
                            background: '#1a56db',
                            hoverBackground: '#1d4ed8',
                            activeBackground: '#1e40af',
                            borderColor: '#1a56db',
                            hoverBorderColor: '#1d4ed8',
                            activeBorderColor: '#1e40af',
                            color: '#ffffff',
                            hoverColor: '#ffffff',
                            activeColor: '#ffffff'
                        },
                        secondary: {
                            background: '{emerald.500}',
                            hoverBackground: '{emerald.600}',
                            activeBackground: '{emerald.700}',
                            borderColor: '{emerald.500}',
                            color: '#ffffff'
                        }
                    }
                },
                dark: {
                    root: {
                        primary: {
                            background: '#3b82f6',
                            hoverBackground: '#60a5fa',
                            activeBackground: '#93c5fd',
                            borderColor: '#3b82f6',
                            color: '#ffffff'
                        }
                    }
                }
            }
        },
        // Add colorful card styling
        card: {
            colorScheme: {
                light: {
                    root: {
                        background: '{surface.0}',
                        borderColor: '#bfdbfe',
                        color: '{surface.700}',
                        shadow: '0 1px 3px 0 rgba(26, 86, 219, 0.1), 0 1px 2px 0 rgba(26, 86, 219, 0.06)'
                    }
                },
                dark: {
                    root: {
                        background: '{surface.900}',
                        borderColor: '#1e40af',
                        color: '{surface.0}'
                    }
                }
            }
        },
        // Colorful badges
        badge: {
            colorScheme: {
                light: {
                    root: {
                        primary: {
                            background: '#1a56db',
                            color: '#ffffff'
                        },
                        secondary: {
                            background: '{emerald.500}',
                            color: '#ffffff'
                        },
                        success: {
                            background: '{green.500}',
                            color: '#ffffff'
                        },
                        info: {
                            background: '{cyan.500}',
                            color: '#ffffff'
                        },
                        warning: {
                            background: '{amber.500}',
                            color: '#ffffff'
                        },
                        danger: {
                            background: '{red.500}',
                            color: '#ffffff'
                        }
                    }
                }
            }
        },
        // Enhanced chart colors
        chart: {
            colorScheme: {
                light: {
                    grid: {
                        color: '#dbeafe'
                    },
                    tick: {
                        color: '{surface.500}'
                    }
                }
            }
        },
        // AutoComplete styling for dark mode support
        autocomplete: {
            colorScheme: {
                light: {
                    root: {
                        background: '{surface.0}',
                        borderColor: '{surface.300}',
                        color: '{surface.700}'
                    },
                    overlay: {
                        background: '{surface.0}',
                        borderColor: '{surface.200}',
                        color: '{surface.700}',
                        shadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'
                    },
                    option: {
                        focusBackground: '{surface.100}',
                        selectedBackground: '{highlight.background}',
                        selectedFocusBackground: '{highlight.focusBackground}',
                        color: '{surface.700}',
                        focusColor: '{surface.700}',
                        selectedColor: '{highlight.color}',
                        selectedFocusColor: '{highlight.focusColor}'
                    }
                },
                dark: {
                    root: {
                        background: '{surface.900}',
                        borderColor: '{surface.700}',
                        color: '{surface.0}'
                    },
                    overlay: {
                        background: '{surface.800}',
                        borderColor: '{surface.700}',
                        color: '{surface.0}',
                        shadow: '0 10px 15px -3px rgba(0, 0, 0, 0.3), 0 4px 6px -2px rgba(0, 0, 0, 0.2)'
                    },
                    option: {
                        focusBackground: '{surface.700}',
                        selectedBackground: '{highlight.background}',
                        selectedFocusBackground: '{highlight.focusBackground}',
                        color: '{surface.0}',
                        focusColor: '{surface.0}',
                        selectedColor: '{highlight.color}',
                        selectedFocusColor: '{highlight.focusColor}'
                    }
                }
            }
        }
    }
});

export default GeopulsePreset;