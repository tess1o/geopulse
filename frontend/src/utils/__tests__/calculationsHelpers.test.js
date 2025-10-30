import { describe, it, expect, vi } from 'vitest'
import { 
  formatDistance, 
  formatDuration, 
  formatSpeed,
  formatDurationCompact,
  formatDurationSmart
} from '../calculationsHelpers.js'

// Mock the useMeasureUnit composable
vi.mock('@/composables/useMeasureUnit', () => ({
  useMeasureUnit: () => ({
    getMeasureUnit: () => 'METRIC' // Default mock
  })
}));

describe('calculationsHelpers', () => {
  describe('formatDistance', () => {
    it('should format meters correctly for distances < 1000m', () => {
      expect(formatDistance(500)).toBe('500 m')
      expect(formatDistance(0)).toBe('0 m')
      expect(formatDistance(999.99)).toBe('999.99 m')
    })

    it('should format kilometers correctly for distances >= 1000m', () => {
      expect(formatDistance(1000)).toBe('1 km')
      expect(formatDistance(1500)).toBe('1.5 km')
      expect(formatDistance(1234.56)).toBe('1.23 km')
      expect(formatDistance(10000)).toBe('10 km')
    })

    it('should handle decimal precision correctly', () => {
      expect(formatDistance(123.456)).toBe('123.46 m')
      expect(formatDistance(1234.567)).toBe('1.23 km')
    })
  })

  describe('formatDuration', () => {
    it('should handle durations less than 1 minute', () => {
      expect(formatDuration(30)).toBe('less than a minute')
      expect(formatDuration(59)).toBe('less than a minute')
    })

    it('should format minutes only (less than 1 hour)', () => {
      expect(formatDuration(60)).toBe('1 minute')
      expect(formatDuration(120)).toBe('2 minutes')
      expect(formatDuration(1800)).toBe('30 minutes')
      expect(formatDuration(3540)).toBe('59 minutes')
    })

    it('should format hours and minutes (less than 1 day)', () => {
      expect(formatDuration(3600)).toBe('1 hour')
      expect(formatDuration(7200)).toBe('2 hours')
      expect(formatDuration(5400)).toBe('1 hour 30 minutes')
      expect(formatDuration(9000)).toBe('2 hours 30 minutes')
    })

    it('should format days and hours (skipping minutes when days > 0)', () => {
      expect(formatDuration(86400)).toBe('1 day')
      expect(formatDuration(172800)).toBe('2 days')
      expect(formatDuration(90000)).toBe('1 day 1 hour')
      expect(formatDuration(180000)).toBe('2 days 2 hours')
    })

    it('should handle singular vs plural correctly', () => {
      expect(formatDuration(60)).toBe('1 minute')
      expect(formatDuration(120)).toBe('2 minutes')
      expect(formatDuration(3600)).toBe('1 hour')
      expect(formatDuration(7200)).toBe('2 hours')
      expect(formatDuration(86400)).toBe('1 day')
      expect(formatDuration(172800)).toBe('2 days')
    })
  })

  describe('formatSpeed', () => {
    it('should format valid speeds correctly', () => {
      expect(formatSpeed(50)).toBe('50.00 km/h')
      expect(formatSpeed(120.5)).toBe('120.50 km/h')
      expect(formatSpeed(0)).toBe('0.00 km/h')
      expect(formatSpeed(15.678)).toBe('15.68 km/h')
    })

    it('should handle string numbers', () => {
      expect(formatSpeed('50')).toBe('50.00 km/h')
      expect(formatSpeed('120.5')).toBe('120.50 km/h')
    })

    it('should handle invalid inputs', () => {
      expect(formatSpeed('abc')).toBe('N/A')
      expect(formatSpeed(null)).toBe('0.00 km/h') // Number(null) = 0
      expect(formatSpeed(undefined)).toBe('N/A')
      expect(formatSpeed('')).toBe('0.00 km/h') // Number('') = 0
    })
  })

  describe('formatDurationCompact', () => {
    it('should format with compact notation', () => {
      expect(formatDurationCompact(3600)).toBe('1h')
      expect(formatDurationCompact(7200)).toBe('2h')
      expect(formatDurationCompact(86400)).toBe('1d')
      expect(formatDurationCompact(90060)).toBe('1d 1h 1m')
    })

    it('should handle zero duration', () => {
      expect(formatDurationCompact(0)).toBe('0m')
    })

    it('should show only non-zero units', () => {
      expect(formatDurationCompact(60)).toBe('1m')
      expect(formatDurationCompact(3600)).toBe('1h')
      expect(formatDurationCompact(86400)).toBe('1d')
      expect(formatDurationCompact(90000)).toBe('1d 1h')
    })

    it('should handle complex durations', () => {
      expect(formatDurationCompact(180120)).toBe('2d 2h 2m')
      expect(formatDurationCompact(266460)).toBe('3d 2h 1m')
    })
  })

  describe('formatDurationSmart', () => {
    describe('minutes (less than 1 hour)', () => {
      it('should format minutes correctly', () => {
        expect(formatDurationSmart(60)).toBe('1 minute')
        expect(formatDurationSmart(120)).toBe('2 minutes')
        expect(formatDurationSmart(1560)).toBe('26 minutes') // Original bug case
        expect(formatDurationSmart(3540)).toBe('59 minutes')
      })

      it('should handle edge cases', () => {
        expect(formatDurationSmart(0)).toBe('0 minutes')
        expect(formatDurationSmart(59)).toBe('0 minutes') // Rounds down
      })
    })

    describe('hours (less than 1 day)', () => {
      it('should format exact hours', () => {
        expect(formatDurationSmart(3600)).toBe('1 hour')
        expect(formatDurationSmart(7200)).toBe('2 hours')
      })

      it('should format hours with minutes', () => {
        expect(formatDurationSmart(5400)).toBe('1 hour 30 minutes')
        expect(formatDurationSmart(9000)).toBe('2 hours 30 minutes')
        expect(formatDurationSmart(9060)).toBe('2 hours 31 minutes')
      })

      it('should handle singular vs plural', () => {
        expect(formatDurationSmart(3660)).toBe('1 hour 1 minute')
        expect(formatDurationSmart(3720)).toBe('1 hour 2 minutes')
      })
    })

    describe('days (less than 1 week)', () => {
      it('should format exact days', () => {
        expect(formatDurationSmart(86400)).toBe('1 day')
        expect(formatDurationSmart(172800)).toBe('2 days')
      })

      it('should format days with hours', () => {
        expect(formatDurationSmart(90000)).toBe('1 day 1 hour')
        expect(formatDurationSmart(180000)).toBe('2 days 2 hours')
      })

      it('should handle 7 days (boundary case)', () => {
        expect(formatDurationSmart(604800)).toBe('7 days')
      })
    })

    describe('weeks (less than 1 month)', () => {
      it('should format weeks correctly', () => {
        expect(formatDurationSmart(1209600)).toBe('2 weeks')
        expect(formatDurationSmart(2419200)).toBe('4 weeks')
      })

      it('should format weeks with days', () => {
        expect(formatDurationSmart(1296000)).toBe('2 weeks 1 day')
        expect(formatDurationSmart(777600)).toBe('1 week 2 days')
      })
    })

    describe('months', () => {
      it('should format months correctly', () => {
        expect(formatDurationSmart(2629746)).toBe('4 weeks 2 days') // ~1 month shows as weeks
        expect(formatDurationSmart(5259492)).toBe('1 month 3 weeks') // ~2 months
      })

      it('should format months with weeks', () => {
        expect(formatDurationSmart(3240000)).toBe('1 month') // ~1.25 months
      })
    })

    describe('real-world examples', () => {
      it('should handle typical durations', () => {
        expect(formatDurationSmart(1800)).toBe('30 minutes') // Short stay
        expect(formatDurationSmart(28800)).toBe('8 hours') // Work day
        expect(formatDurationSmart(4500)).toBe('1 hour 15 minutes') // Lunch break
        expect(formatDurationSmart(259200)).toBe('3 days') // Weekend trip
      })
    })

    describe('boundary conditions', () => {
      it('should handle hour boundaries correctly', () => {
        expect(formatDurationSmart(3599)).toBe('59 minutes') // 1 second before 1 hour
        expect(formatDurationSmart(3600)).toBe('1 hour') // Exactly 1 hour
        expect(formatDurationSmart(3601)).toBe('1 hour') // 1 second after (rounds down minutes)
      })

      it('should handle day boundaries correctly', () => {
        expect(formatDurationSmart(86399)).toBe('23 hours 59 minutes') // 1 second before 1 day
        expect(formatDurationSmart(86400)).toBe('1 day') // Exactly 1 day
        expect(formatDurationSmart(86401)).toBe('1 day') // 1 second after (rounds down hours)
      })

      it('should handle week boundaries correctly', () => {
        expect(formatDurationSmart(604799)).toBe('6 days 23 hours') // 1 second before 1 week
        expect(formatDurationSmart(604800)).toBe('7 days') // Exactly 1 week (shows as days)
        expect(formatDurationSmart(604801)).toBe('7 days') // 1 second after 1 week
      })
    })

    describe('regression tests', () => {
      it('should never show "0 hours" for minute-only durations', () => {
        // These were problematic before the fix
        expect(formatDurationSmart(300)).toBe('5 minutes')
        expect(formatDurationSmart(1560)).toBe('26 minutes')
        expect(formatDurationSmart(3540)).toBe('59 minutes')
        
        // Verify no duration under 1 hour contains "hour"
        for (let minutes = 1; minutes < 60; minutes++) {
          const result = formatDurationSmart(minutes * 60)
          expect(result).toMatch(/^\d+ minutes?$/)
          expect(result).not.toContain('hour')
        }
      })
    })
  })

  describe('duration function consistency', () => {
    it('should have consistent behavior for same inputs across different functions', () => {
      const testDurations = [0, 60, 3600, 86400, 90000]
      
      testDurations.forEach(duration => {
        // All functions should handle the same inputs without errors
        expect(() => formatDuration(duration)).not.toThrow()
        expect(() => formatDurationCompact(duration)).not.toThrow()
        expect(() => formatDurationSmart(duration)).not.toThrow()
        
        // All should return strings
        expect(typeof formatDuration(duration)).toBe('string')
        expect(typeof formatDurationCompact(duration)).toBe('string')
        expect(typeof formatDurationSmart(duration)).toBe('string')
      })
    })

    it('should have different but reasonable outputs for same input', () => {
      const duration = 5400 // 1.5 hours
      
      expect(formatDuration(duration)).toBe('1 hour 30 minutes')
      expect(formatDurationCompact(duration)).toBe('1h 30m')
      expect(formatDurationSmart(duration)).toBe('1 hour 30 minutes')
    })
  })
})

describe('calculationsHelpers with Imperial units', () => {
  beforeEach(() => {
    vi.mock('@/composables/useMeasureUnit', () => ({
      useMeasureUnit: () => ({
        getMeasureUnit: () => 'IMPERIAL'
      })
    }));
  });

  describe('formatDistance', () => {
    it('should format feet correctly for distances < 1 mile', () => {
      expect(formatDistance(100)).toBe('328 ft');
      expect(formatDistance(0)).toBe('0 ft');
    });

    it('should format miles correctly for distances >= 1 mile', () => {
      expect(formatDistance(1609.34)).toBe('1.00 mi');
      expect(formatDistance(3218.68)).toBe('2.00 mi');
    });
  });

  describe('formatDistanceRounded', () => {
    it('should format feet correctly for distances < 1 mile', () => {
      expect(formatDistanceRounded(100)).toBe('328 ft');
    });

    it('should format miles correctly for distances >= 1 mile', () => {
      expect(formatDistanceRounded(1609.34)).toBe('1 mi');
    });
  });

  describe('formatSpeed', () => {
    it('should format mph correctly', () => {
      expect(formatSpeed(50)).toBe('31.07 mph');
      expect(formatSpeed(120.5)).toBe('74.88 mph');
    });
  });
});
